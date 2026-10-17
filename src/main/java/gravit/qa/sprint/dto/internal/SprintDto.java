package gravit.qa.sprint.dto.internal;

import gravit.qa.sprint.domain.PrepareFailureReason;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SprintDto(
        long id,
        String sprintKey,
        String notionPageId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String figmaFileKey,
        SprintStatus status,
        PrepareFailureReason prepareFailureReason,
        List<WarningDto> warnings,
        LocalDateTime preparedAt,
        LocalDateTime confirmedAt,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        long createdBy,
        int version
) {

    public static SprintDto from(Sprint sprint) {
        return new SprintDto(
                sprint.getId(),
                sprint.getSprintKey(),
                sprint.getNotionPageId(),
                sprint.getPeriodStart(),
                sprint.getPeriodEnd(),
                sprint.getFigmaFileKey(),
                sprint.getStatus(),
                sprint.getPrepareFailureReason(),
                List.copyOf(sprint.getWarnings()),
                sprint.getPreparedAt(),
                sprint.getConfirmedAt(),
                sprint.getClosedAt(),
                sprint.getCreatedAt(),
                sprint.getCreatedBy(),
                sprint.getVersion()
        );
    }
}
