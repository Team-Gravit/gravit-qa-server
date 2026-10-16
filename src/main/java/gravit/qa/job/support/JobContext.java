package gravit.qa.job.support;

import gravit.qa.global.util.KstTimes;
import gravit.qa.job.domain.JobStepStatus;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.dto.internal.ClaimedJobDto;
import gravit.qa.job.repository.custom.JobQueueRepository;

import java.time.Clock;
import java.util.function.Supplier;

public class JobContext {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final ClaimedJobDto job;
    private final JobQueueRepository jobQueueRepository;
    private final Clock clock;

    public JobContext(
            ClaimedJobDto job,
            JobQueueRepository jobQueueRepository,
            Clock clock
    ) {
        this.job = job;
        this.jobQueueRepository = jobQueueRepository;
        this.clock = clock;
    }

    public long jobId() {
        return job.id();
    }

    public JobType type() {
        return job.type();
    }

    public Long sprintId() {
        return job.sprintId();
    }

    public String payload() {
        return job.payload();
    }

    public int attempt() {
        return job.attempt();
    }

    public <T> T step(
            String stepName,
            Supplier<T> work
    ) {
        checkCancelled();

        int seq = job.type().seqOf(stepName);
        jobQueueRepository.startStep(job.id(), seq, KstTimes.now(clock));
        try {
            T result = work.get();
            jobQueueRepository.finishStep(job.id(), seq, JobStepStatus.DONE, null, KstTimes.now(clock));
            return result;
        } catch (RuntimeException e) {
            jobQueueRepository.finishStep(job.id(), seq, JobStepStatus.FAILED, truncate(e.getMessage()), KstTimes.now(clock));
            throw e;
        }
    }

    public void step(
            String stepName,
            Runnable work
    ) {
        step(stepName, () -> {
            work.run();
            return null;
        });
    }

    public void skipStep(
            String stepName,
            String reason
    ) {
        int seq = job.type().seqOf(stepName);
        jobQueueRepository.finishStep(job.id(), seq, JobStepStatus.SKIPPED, truncate(reason), KstTimes.now(clock));
    }

    public void checkCancelled() {
        if (jobQueueRepository.isCancelRequested(job.id())) {
            throw new JobCancelledException(job.id());
        }
    }

    static String truncate(String message) {
        if (message == null || message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
