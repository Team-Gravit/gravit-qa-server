package gravit.qa.sprint.dto.internal;

import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;

import java.time.LocalDateTime;

public record SprintDto(
        long id,
        String sprintKey,
        String notionPageId,
        String changeSummary,
        String figmaFileKey,
        SprintStatus status,
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
                sprint.getChangeSummary(),
                sprint.getFigmaFileKey(),
                sprint.getStatus(),
                sprint.getClosedAt(),
                sprint.getCreatedAt(),
                sprint.getCreatedBy(),
                sprint.getVersion()
        );
    }
}
