package gravit.qa.job.repository.custom;

import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobStepStatus;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.dto.internal.ClaimedJobDto;
import gravit.qa.job.dto.internal.JobDto;
import gravit.qa.job.dto.internal.JobStepDto;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobQueueRepository {

    long insertJob(
            JobType type,
            Long sprintId,
            String payload,
            int maxAttempts,
            LocalDateTime now
    );

    void insertSteps(
            long jobId,
            List<String> stepNames
    );

    Optional<JobDto> findJob(long jobId);

    List<JobStepDto> findSteps(long jobId);

    Optional<ClaimedJobDto> claimNext(
            Collection<JobType> types,
            String workerId,
            LocalDateTime now
    );

    void resetSteps(long jobId);

    void startStep(
            long jobId,
            int seq,
            LocalDateTime now
    );

    void finishStep(
            long jobId,
            int seq,
            JobStepStatus status,
            String message,
            LocalDateTime now
    );

    boolean isCancelRequested(long jobId);

    int heartbeat(
            String workerId,
            LocalDateTime now
    );

    boolean markSucceeded(
            long jobId,
            String workerId,
            LocalDateTime now
    );

    boolean markCancelled(
            long jobId,
            String workerId,
            LocalDateTime now
    );

    Optional<JobStatus> markFailed(
            long jobId,
            String workerId,
            boolean retryable,
            String error,
            LocalDateTime retryAt,
            LocalDateTime now
    );

    Optional<JobStatus> requestCancel(
            long jobId,
            LocalDateTime now
    );

    List<Long> reapStale(
            LocalDateTime staleBefore,
            String error,
            LocalDateTime now
    );
}
