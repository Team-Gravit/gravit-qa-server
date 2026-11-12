package gravit.qa.designReview.dto.response;

import gravit.qa.checklist.domain.CellSource;
import gravit.qa.checklist.domain.CellState;
import gravit.qa.designReview.domain.DesignReviewItemHistory;
import gravit.qa.global.util.KstTimes;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record DesignReviewHistoryResponse(
        @Schema(
                description = "바뀐 뒤 상태",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        CellState state,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CellSource source,

        @Schema(description = "자동 비교 사유 또는 판정 초기화 사유. 사람이 상태를 바꾸면 null")
        String reason,

        @Schema(description = "바꾼 관리자 ID. 자동 비교면 null")
        Long updatedBy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime updatedAt
) {

    public static DesignReviewHistoryResponse from(DesignReviewItemHistory history) {
        return new DesignReviewHistoryResponse(
                history.getToState(),
                history.getSource(),
                history.getReason(),
                history.getChangedBy(),
                KstTimes.toOffset(history.getChangedAt())
        );
    }
}
