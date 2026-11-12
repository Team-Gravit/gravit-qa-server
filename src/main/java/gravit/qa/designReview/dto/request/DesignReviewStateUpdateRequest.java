package gravit.qa.designReview.dto.request;

import gravit.qa.checklist.domain.CellState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record DesignReviewStateUpdateRequest(
        @NotNull
        @Schema(
                description = "UNCHECKED, PASS, FAIL, NOT_APPLICABLE 중 하나. NOT_APPLICABLE은 사람이 해당 없음으로 처리할 때 쓴다",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        CellState state,

        @NotNull
        @Schema(
                description = "디자인 검수 항목 낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Integer version
) {
}
