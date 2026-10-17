package gravit.qa.job.support;

import gravit.qa.global.util.KstTimes;
import gravit.qa.job.config.JobProps;
import gravit.qa.job.config.WorkerProps;
import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.dto.event.JobTerminatedEvent;
import gravit.qa.job.dto.internal.ClaimedJobDto;
import gravit.qa.job.dto.internal.ReapedJobDto;
import gravit.qa.job.repository.custom.JobQueueRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class JobRunner {

    private static final String HEARTBEAT_TIMEOUT_ERROR = "heartbeat timeout";
    private static final String UNKNOWN_HOST = "unknown-host";
    private static final int WORKER_SUFFIX_LENGTH = 8;

    private final Map<JobType, JobHandler> typeToHandler;
    private final Set<JobType> consumableTypes;
    @Getter
    private final String workerId;

    private final JobQueueRepository jobQueueRepository;

    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final JobProps jobProps;

    public JobRunner(
            List<JobHandler> jobHandlers,
            JobQueueRepository jobQueueRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher,
            TransactionTemplate transactionTemplate,
            JobProps jobProps,
            WorkerProps workerProps
    ) {
        this.typeToHandler = toHandlerMap(jobHandlers);
        this.consumableTypes = resolveConsumableTypes(workerProps.jobTypes(), typeToHandler);
        this.workerId = resolveWorkerId(workerProps.id());
        this.jobQueueRepository = jobQueueRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.jobProps = jobProps;

        log.info("작업 워커 준비: workerId={}, types={}", workerId, consumableTypes);
    }

    public boolean runNext() {
        Optional<ClaimedJobDto> claimed = jobQueueRepository.claimNext(consumableTypes, workerId, now());
        if (claimed.isEmpty()) {
            return false;
        }

        execute(claimed.get());
        return true;
    }

    public int heartbeat() {
        return jobQueueRepository.heartbeat(workerId, now());
    }

    public List<Long> reapStale() {
        LocalDateTime now = now();
        List<ReapedJobDto> reaped = transactionTemplate.execute(status -> {
            List<ReapedJobDto> rows = jobQueueRepository.reapStale(
                    now.minus(jobProps.heartbeatTimeout()),
                    HEARTBEAT_TIMEOUT_ERROR,
                    now
            );
            rows.stream()
                    .filter(ReapedJobDto::isTerminated)
                    .forEach(row -> eventPublisher.publishEvent(JobTerminatedEvent.of(row.id(), row.type(), row.sprintId(), row.status(), null)));
            return rows;
        });

        List<Long> reapedIds = reaped == null ? List.of() : reaped.stream().map(ReapedJobDto::id).toList();
        if (!reapedIds.isEmpty()) {
            log.warn("heartbeat가 끊긴 작업을 회수했습니다: {}", reapedIds);
        }
        return reapedIds;
    }

    private void execute(ClaimedJobDto job) {
        jobQueueRepository.resetSteps(job.id());
        JobContext context = new JobContext(job, jobQueueRepository, clock);
        JobHandler handler = typeToHandler.get(job.type());

        try {
            handler.handle(context);
            if (!jobQueueRepository.markSucceeded(job.id(), workerId, now())) {
                log.warn("작업 완료를 기록하지 못했습니다(점유 상실). jobId={}", job.id());
            }
        } catch (JobCancelledException e) {
            cancel(job, e);
        } catch (NonRetryableJobException e) {
            log.warn("작업 실패(재시도 없음). jobId={}, reason={}", job.id(), e.getMessage());
            fail(job, false, e);
        } catch (RuntimeException e) {
            log.error("작업 실패. jobId={}, attempt={}/{}", job.id(), job.attempt(), job.maxAttempts(), e);
            fail(job, true, e);
        }
    }

    private void cancel(
            ClaimedJobDto job,
            JobCancelledException cause
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            if (jobQueueRepository.markCancelled(job.id(), workerId, now())) {
                eventPublisher.publishEvent(JobTerminatedEvent.of(job.id(), job.type(), job.sprintId(), JobStatus.CANCELLED, cause));
            }
        });
    }

    private void fail(
            ClaimedJobDto job,
            boolean retryable,
            RuntimeException cause
    ) {
        LocalDateTime now = now();
        transactionTemplate.executeWithoutResult(status -> {
            Optional<JobStatus> result = jobQueueRepository.markFailed(
                    job.id(),
                    workerId,
                    retryable,
                    JobContext.truncate(cause.getClass().getSimpleName() + ": " + cause.getMessage()),
                    now.plus(backoff(job.attempt())),
                    now
            );
            if (result.isEmpty()) {
                log.warn("작업 실패를 기록하지 못했습니다(점유 상실). jobId={}", job.id());
                return;
            }
            if (result.get() == JobStatus.FAILED) {
                eventPublisher.publishEvent(JobTerminatedEvent.of(job.id(), job.type(), job.sprintId(), JobStatus.FAILED, cause));
            }
        });
    }

    Duration backoff(int attempt) {
        return jobProps.backoffBase().multipliedBy(1L << Math.max(0, attempt - 1));
    }

    private LocalDateTime now() {
        return KstTimes.now(clock);
    }

    private static Map<JobType, JobHandler> toHandlerMap(List<JobHandler> jobHandlers) {
        Map<JobType, JobHandler> handlers = new EnumMap<>(JobType.class);
        for (JobHandler handler : jobHandlers) {
            JobHandler previous = handlers.put(handler.type(), handler);
            if (previous != null) {
                throw new IllegalStateException(handler.type() + " 작업 처리기가 둘 이상입니다.");
            }
        }
        return handlers;
    }

    private static Set<JobType> resolveConsumableTypes(
            List<JobType> configuredTypes,
            Map<JobType, JobHandler> typeToHandler
    ) {
        Set<JobType> types = EnumSet.noneOf(JobType.class);
        for (JobType type : configuredTypes) {
            if (typeToHandler.containsKey(type)) {
                types.add(type);
            } else {
                log.warn("{} 작업 처리기가 없어 소비 대상에서 제외합니다.", type);
            }
        }
        return types;
    }

    private static String resolveWorkerId(String configuredId) {
        if (configuredId != null && !configuredId.isBlank()) {
            return configuredId;
        }
        String suffix = UUID.randomUUID().toString().substring(0, WORKER_SUFFIX_LENGTH);
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + suffix;
        } catch (UnknownHostException e) {
            return UNKNOWN_HOST + "-" + suffix;
        }
    }
}
