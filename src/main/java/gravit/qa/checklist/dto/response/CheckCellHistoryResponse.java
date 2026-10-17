package gravit.qa.checklist.dto.response;

import gravit.qa.checklist.domain.CellSource;
import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCellHistory;
import gravit.qa.global.util.KstTimes;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record CheckCellHistoryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CellState fromState,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CellState toState,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CellSource source,

        @Schema(description = "변경자 adminId. 자동 판정이면 null")
        Long changedBy,

        @Schema(description = "자동 판정한 실행 ID")
        Long autoRunId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime changedAt
) {

    public static CheckCellHistoryResponse from(CheckCellHistory history) {
        return new CheckCellHistoryResponse(
                history.getFromState(),
                history.getToState(),
                history.getSource(),
                history.getChangedBy(),
                history.getAutoRunId(),
                KstTimes.toOffset(history.getChangedAt())
        );
    }
}
