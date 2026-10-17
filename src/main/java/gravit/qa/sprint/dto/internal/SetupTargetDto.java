package gravit.qa.sprint.dto.internal;

import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;

public record SetupTargetDto(
        long sprintId,
        String sprintKey,
        String notionPageId,
        SprintStatus status
) {

    public static SetupTargetDto from(Sprint sprint) {
        return new SetupTargetDto(
                sprint.getId(),
                sprint.getSprintKey(),
                sprint.getNotionPageId(),
                sprint.getStatus()
        );
    }
}
