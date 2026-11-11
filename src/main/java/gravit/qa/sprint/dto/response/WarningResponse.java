package gravit.qa.sprint.dto.response;

import gravit.qa.sprint.domain.WarningCode;
import gravit.qa.sprint.dto.internal.WarningDto;
import io.swagger.v3.oas.annotations.media.Schema;

public record WarningResponse(
        @Schema(
                description = "경고 코드",
                example = "SPEC_READ_FAILED",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        WarningCode code,

        @Schema(
                description = "사람이 읽는 설명",
                example = "QUIZ-02: 본문 첫 블록이 기본 정보 표가 아닙니다",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String message
) {

    public static WarningResponse from(WarningDto warning) {
        return new WarningResponse(warning.code(), warning.message());
    }
}
