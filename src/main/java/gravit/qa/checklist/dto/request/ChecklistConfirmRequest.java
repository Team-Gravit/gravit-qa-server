package gravit.qa.checklist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ChecklistConfirmRequest(
        @Schema(
                description = "AUTO 항목이 있으면 자동 실행을 큐에 넣는다. 기본 true",
                defaultValue = "true"
        )
        Boolean startAutoRun,

        @NotNull
        @Schema(
                description = "스프린트 낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Integer version
) {

    public boolean shouldStartAutoRun() {
        return !Boolean.FALSE.equals(startAutoRun);
    }
}
