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
        Integer version,

        @Schema(
                description = "Fail 사유(선택). state가 FAIL일 때만 저장하고 그 밖에는 무시한다. 앞뒤 공백을 지운 뒤 500자까지, 빈 문자열은 null로 본다. 이미 FAIL인 항목에 다른 사유를 보내면 사유만 바꾼다",
                example = "로그인 버튼 순서가 명세와 반대다"
        )
        String reason
) {
}
