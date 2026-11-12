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
                description = "행위",
                example = "로그인 제공자 버튼 순서를 표시한다",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String title,

        @NotBlank
        @Schema(
                description = "설명(세부 확인 내용)",
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
        @Schema(
                description = "AUTO면 1차, MANUAL이면 2차 체크리스트",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Classification classification
) {
}
