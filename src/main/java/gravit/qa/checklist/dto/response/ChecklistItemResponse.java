package gravit.qa.checklist.dto.response;

import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.domain.ItemOrigin;
import gravit.qa.feature.domain.Part;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ChecklistItemResponse(
        @Schema(
                description = "체크 항목 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long id,

        @Schema(
                description = "기능 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long featureId,

        @Schema(
                description = "행위",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String title,

        @Schema(
                description = "설명(세부 확인 내용)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String expected,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Part> parts,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Classification classification,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ItemOrigin origin,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<CheckCellResponse> cells,

        @Schema(
                description = "낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int version
) {

    public static ChecklistItemResponse of(
            ChecklistItem item,
            List<CheckCellResponse> cells
    ) {
        return new ChecklistItemResponse(
                item.getId(),
                item.getFeatureId(),
                item.getTitle(),
                item.getExpected(),
                item.getParts(),
                item.getClassification(),
                item.getOrigin(),
                cells,
                item.getVersion()
        );
    }
}
