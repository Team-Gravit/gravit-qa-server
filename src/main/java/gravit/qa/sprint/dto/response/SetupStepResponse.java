package gravit.qa.sprint.dto.response;

import gravit.qa.sprint.domain.SetupStepKey;
import gravit.qa.sprint.domain.SetupStepStatus;
import gravit.qa.sprint.dto.internal.SetupStepDto;
import io.swagger.v3.oas.annotations.media.Schema;

public record SetupStepResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SetupStepKey key,

        @Schema(
                description = "WAITING은 수정 이력 반영 승인 대기",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        SetupStepStatus status,

        @Schema(
                description = "이 단계를 시작한 횟수",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int attempt,

        @Schema(description = "단계 결과 요약이나 실패 사유")
        String message
) {

    public static SetupStepResponse from(SetupStepDto step) {
        return new SetupStepResponse(step.key(), step.status(), step.attempt(), step.message());
    }
}
