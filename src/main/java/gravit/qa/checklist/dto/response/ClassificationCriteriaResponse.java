package gravit.qa.checklist.dto.response;

import gravit.qa.checklist.dto.internal.ClassificationCriteriaDto;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClassificationCriteriaResponse(
        @Schema(
                description = "명세만으로 판정 가능",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        CriterionResultResponse specDecidable,

        @Schema(
                description = "사전 조건 재현 가능",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        CriterionResultResponse preconditionReproducible,

        @Schema(
                description = "결과 관찰 가능",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        CriterionResultResponse observable,

        @Schema(
                description = "외부 의존 없음",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        CriterionResultResponse noExternalDependency,

        @Schema(
                description = "주관 판단 불필요",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        CriterionResultResponse notSubjective
) {

    public static ClassificationCriteriaResponse from(ClassificationCriteriaDto criteria) {
        if (criteria == null) {
            return null;
        }
        return new ClassificationCriteriaResponse(
                CriterionResultResponse.from(criteria.specDecidable()),
                CriterionResultResponse.from(criteria.preconditionReproducible()),
                CriterionResultResponse.from(criteria.observable()),
                CriterionResultResponse.from(criteria.noExternalDependency()),
                CriterionResultResponse.from(criteria.notSubjective())
        );
    }
}
