package gravit.qa.checklist.dto.response;

import gravit.qa.feature.dto.response.FeatureResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ChecklistFeatureResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        FeatureResponse feature,

        @Schema(
                description = "준비 경고 코드",
                example = "[\"FIGMA_FRAME_MISSING\"]"
        )
        List<String> warnings,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ChecklistItemResponse> items
) {

    public static ChecklistFeatureResponse of(
            FeatureResponse feature,
            List<String> warnings,
            List<ChecklistItemResponse> items
    ) {
        return new ChecklistFeatureResponse(feature, List.copyOf(warnings), List.copyOf(items));
    }
}
