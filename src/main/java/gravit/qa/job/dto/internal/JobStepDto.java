package gravit.qa.job.dto.internal;

import gravit.qa.job.domain.JobStepStatus;

import java.time.LocalDateTime;

public record JobStepDto(
        int seq,
        String name,
        JobStepStatus status,
        String message,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
