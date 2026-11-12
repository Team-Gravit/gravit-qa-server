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
        Integer version,

        @Schema(
                description = "Fail 사유(선택). state가 FAIL일 때만 저장하고 그 밖에는 무시한다. 앞뒤 공백을 지운 뒤 500자까지, 빈 문자열은 null로 본다. 이미 FAIL인 셀에 다른 사유를 보내면 사유만 바꾼다",
                example = "응답 DTO에 이전·다음 공지 필드가 없다"
        )
        String reason
) {
}
