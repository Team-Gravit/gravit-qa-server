package gravit.qa.job.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.global.util.KstTimes;
import gravit.qa.job.config.JobProps;
import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.dto.event.JobTerminatedEvent;
import gravit.qa.job.dto.internal.JobDto;
import gravit.qa.job.dto.response.JobResponse;
import gravit.qa.job.repository.custom.JobQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static gravit.qa.global.exception.domain.CustomErrorCode.JOB_ALREADY_ACTIVE;
import static gravit.qa.global.exception.domain.CustomErrorCode.JOB_NOT_CANCELLABLE;
import static gravit.qa.global.exception.domain.CustomErrorCode.JOB_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class JobCommandService {

    private final JobQueueRepository jobQueueRepository;

    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final JobProps jobProps;

    @Transactional
    public long enqueue(
            JobType type,
            Long sprintId,
            Map<String, Object> payload
    ) {
        LocalDateTime now = KstTimes.now(clock);
        try {
            long jobId = jobQueueRepository.insertJob(type, sprintId, toJson(payload), jobProps.maxAttempts(), now);
            jobQueueRepository.insertSteps(jobId, type.getStepNames());
            return jobId;
        } catch (DuplicateKeyException e) {
            throw new RestApiException(JOB_ALREADY_ACTIVE);
        }
    }

    @Transactional
    public JobResponse requestCancel(long jobId) {
        Optional<JobStatus> cancelled = jobQueueRepository.requestCancel(jobId, KstTimes.now(clock));
        if (cancelled.isEmpty()) {
            JobDto job = jobQueueRepository.findJob(jobId)
                    .orElseThrow(() -> new RestApiException(JOB_NOT_FOUND));
            if (!job.status().isActive()) {
                throw new RestApiException(JOB_NOT_CANCELLABLE);
            }
        }

        JobDto job = jobQueueRepository.findJob(jobId)
                .orElseThrow(() -> new RestApiException(JOB_NOT_FOUND));
        if (cancelled.filter(JobStatus.CANCELLED::equals).isPresent()) {
            eventPublisher.publishEvent(JobTerminatedEvent.of(job.id(), job.type(), job.sprintId(), JobStatus.CANCELLED, null));
        }
        return JobResponse.of(job, jobQueueRepository.findSteps(jobId));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("작업 payload를 JSON으로 변환할 수 없습니다.", e);
        }
    }
}
