package gravit.qa.checklist.dto.response;

import gravit.qa.checklist.dto.internal.CriterionResultDto;
import io.swagger.v3.oas.annotations.media.Schema;

public record CriterionResultResponse(
        @Schema(
                description = "기준 충족 여부",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean met,

        @Schema(
                description = "판정 근거",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String reason
) {

    public static CriterionResultResponse from(CriterionResultDto criterion) {
        return new CriterionResultResponse(criterion.met(), criterion.reason());
    }
}
