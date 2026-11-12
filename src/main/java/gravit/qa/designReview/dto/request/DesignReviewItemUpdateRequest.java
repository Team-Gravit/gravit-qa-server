package gravit.qa.designReview.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DesignReviewItemUpdateRequest(
        @Size(min = 1)
        @Schema(description = "행위")
        String title,

        @Size(min = 1)
        @Schema(description = "설명(비교 내용과 명세 행 번호)")
        String expected,

        @NotNull
        @Schema(
                description = "디자인 검수 항목 낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Integer version
) {
}
