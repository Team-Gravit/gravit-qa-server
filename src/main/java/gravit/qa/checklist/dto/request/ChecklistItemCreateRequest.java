package gravit.qa.checklist.dto.request;

import gravit.qa.checklist.domain.Classification;
import gravit.qa.feature.domain.Part;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ChecklistItemCreateRequest(
        @NotNull
        @Schema(
                description = "기능 ID",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Long featureId,

        @NotBlank
        @Schema(
                description = "검수 항목",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String title,

        @NotBlank
        @Schema(
                description = "기대 결과",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String expected,

        @NotEmpty
        @Schema(
                description = "적용 파트(1개 이상)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Set<Part> parts,

        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Classification classification,

        @Schema(description = "테스트 카탈로그 ID. AUTO면 필수")
        String testRef,

        @Schema(
                description = "사전 조건 페르소나 키",
                example = "learner"
        )
        String personaKey
) {
}
