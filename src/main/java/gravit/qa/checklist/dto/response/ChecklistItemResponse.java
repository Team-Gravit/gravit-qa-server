package gravit.qa.checklist.dto.response;

import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.domain.ItemOrigin;
import gravit.qa.checklist.domain.ReviewState;
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
                description = "검수 항목",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String title,

        @Schema(
                description = "명세 근거 기대 결과",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String expected,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Part> parts,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Classification classification,

        @Schema(description = "자동/수동 분류 기준 5개 판정. 사람이 추가한 항목은 null")
        ClassificationCriteriaResponse criteria,

        @Schema(
                description = "기준은 충족하나 대응 테스트가 없어 이번에는 수동",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean automationGap,

        @Schema(description = "테스트 카탈로그 ID")
        String testRef,

        @Schema(
                description = "사전 조건 페르소나 키",
                example = "learner"
        )
        String personaKey,

        @Schema(description = "근거 스냅샷 ID")
        List<Long> sourceRefs,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ItemOrigin origin,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ReviewState reviewState,

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
                ClassificationCriteriaResponse.from(item.getCriteria()),
                item.isAutomationGap(),
                item.getTestRef(),
                item.getPersonaKey(),
                item.getSourceRefs(),
                item.getOrigin(),
                item.getReviewState(),
                cells,
                item.getVersion()
        );
    }
}
