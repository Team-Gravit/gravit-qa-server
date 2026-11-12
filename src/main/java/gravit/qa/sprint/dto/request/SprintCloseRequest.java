package gravit.qa.sprint.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record SprintCloseRequest(
        @NotNull
        @Schema(
                description = "스프린트 낙관적 잠금 값",
                example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Integer version
) {
}
