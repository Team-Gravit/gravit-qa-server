package gravit.qa.job.dto.internal;

import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobType;

import java.time.LocalDateTime;

public record JobDto(
        long id,

        JobType type,

        Long sprintId,

        JobStatus status,

        int attempt,

        int maxAttempts,

        LocalDateTime runAfter,

        boolean cancelRequested,

        String lastError,

        LocalDateTime createdAt,

        LocalDateTime finishedAt
) {
}
