package gravit.qa.job.dto.internal;

import gravit.qa.job.domain.JobType;

public record ClaimedJobDto(
        long id,

        JobType type,

        Long sprintId,

        String payload,

        int attempt,

        int maxAttempts
) {
}
