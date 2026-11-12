package gravit.qa.checklist.dto.response;

import gravit.qa.checklist.domain.CellSource;
import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCellHistory;
import gravit.qa.global.util.KstTimes;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record CheckCellHistoryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CellState fromState,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CellState toState,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CellSource source,

        @Schema(description = "1차 판정 사유, 판정 초기화 사유, 또는 사람이 Fail로 바꿀 때 남긴 사유")
        String reason,

        @Schema(
                description = "1차 판정 근거 코드 위치. 사람이 FAIL 사유만 고치면 셀에 남은 근거, 그 밖의 사람 변경과 판정 초기화는 빈 배열",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<CodeEvidenceResponse> evidence,

        @Schema(description = "변경한 관리자 ID. 1차 판정이면 null")
        Long changedBy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime changedAt
) {

    public static CheckCellHistoryResponse from(CheckCellHistory history) {
        return new CheckCellHistoryResponse(
                history.getFromState(),
                history.getToState(),
                history.getSource(),
                history.getReason(),
                CodeEvidenceResponse.fromAll(history.getEvidence()),
                history.getChangedBy(),
                KstTimes.toOffset(history.getChangedAt())
        );
    }
}
