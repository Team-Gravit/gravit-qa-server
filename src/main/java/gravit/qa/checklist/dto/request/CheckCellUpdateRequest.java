package gravit.qa.checklist.dto.request;

import gravit.qa.checklist.domain.CellState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CheckCellUpdateRequest(
        @NotNull
        @Schema(
                description = "UNCHECKED, PASS, FAIL 중 하나",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        CellState state,

        @NotNull
        @Schema(
                description = "셀 낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Integer version
) {
}
