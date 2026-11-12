package gravit.qa.job.dto.event;

import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobType;

public record JobTerminatedEvent(
        long jobId,
        JobType type,
        Long sprintId,
        JobStatus status,
        Throwable cause
) {

    public static JobTerminatedEvent of(
            long jobId,
            JobType type,
            Long sprintId,
            JobStatus status,
            Throwable cause
    ) {
        return new JobTerminatedEvent(jobId, type, sprintId, status, cause);
    }
}
