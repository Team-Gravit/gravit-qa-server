package gravit.qa.job.dto.internal;

import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobType;

public record ReapedJobDto(
        long id,
        JobType type,
        Long sprintId,
        JobStatus status
) {

    public boolean isTerminated() {
        return status == JobStatus.FAILED || status == JobStatus.CANCELLED;
    }
}
