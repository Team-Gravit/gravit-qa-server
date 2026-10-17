package gravit.qa.checklist.dto.response;

import gravit.qa.sprint.domain.SprintStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ChecklistResponse(
        @Schema(
                description = "스프린트 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long sprintId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SprintStatus sprintStatus,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ChecklistFeatureResponse> features
) {

    public static ChecklistResponse of(
            long sprintId,
            SprintStatus sprintStatus,
            List<ChecklistFeatureResponse> features
    ) {
        return new ChecklistResponse(sprintId, sprintStatus, List.copyOf(features));
    }
}
