package gravit.qa.checklist.dto.response;

import gravit.qa.checklist.domain.CellSource;
import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.feature.domain.Part;
import gravit.qa.global.util.KstTimes;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record CheckCellResponse(
        @Schema(
                description = "체크 항목 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long itemId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Part part,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CellState state,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CellSource source,

        @Schema(description = "자동 판정한 실행 ID")
        Long autoRunId,

        @Schema(description = "현재 사용자가 수동 변경 가능한지")
        boolean editable,

        @Schema(description = "마지막 변경자 adminId")
        Long updatedBy,

        @Schema(description = "마지막 변경 시각")
        OffsetDateTime updatedAt,

        @Schema(
                description = "낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int version
) {

    public static CheckCellResponse of(
            CheckCell cell,
            boolean editable
    ) {
        return new CheckCellResponse(
                cell.getItemId(),
                cell.getPart(),
                cell.getState(),
                cell.getSource(),
                cell.getAutoRunId(),
                editable,
                cell.getUpdatedBy(),
                KstTimes.toOffset(cell.getUpdatedAt()),
                cell.getVersion()
        );
    }
}
