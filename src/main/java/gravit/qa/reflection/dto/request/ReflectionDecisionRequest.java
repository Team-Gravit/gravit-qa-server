package gravit.qa.reflection.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ReflectionDecisionRequest(
        @NotNull
        @Schema(
                description = "반영 제안 낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Integer version
) {
}
