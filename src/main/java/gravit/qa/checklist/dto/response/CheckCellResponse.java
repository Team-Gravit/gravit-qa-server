package gravit.qa.checklist.dto.response;

import gravit.qa.checklist.domain.CellSource;
import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.feature.domain.Part;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CheckCellResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Part part,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CellState state,

        @Schema(
                description = "AUTO면 1차 판정, MANUAL이면 사람이 바꾼 결과",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        CellSource source,

        @Schema(description = "1차 판정 사유 또는 판정 초기화 사유. 사람이 바꾸면 null")
        String reason,

        @Schema(
                description = "1차 판정 근거 코드 위치. 1차 판정이 아니면 빈 배열",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<CodeEvidenceResponse> evidence,

        @Schema(
                description = "지금 사람이 바꿀 수 있는지",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean editable,

        @Schema(
                description = "셀 낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int version
) {

    public static CheckCellResponse of(
            CheckCell cell,
            boolean editable
    ) {
        return new CheckCellResponse(
                cell.getPart(),
                cell.getState(),
                cell.getSource(),
                cell.getReason(),
                CodeEvidenceResponse.fromAll(cell.getEvidence()),
                editable,
                cell.getVersion() == null ? 0 : cell.getVersion()
        );
    }
}
