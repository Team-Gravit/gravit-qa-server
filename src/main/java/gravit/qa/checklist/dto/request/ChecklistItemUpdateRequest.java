package gravit.qa.checklist.dto.request;

import gravit.qa.checklist.domain.Classification;
import gravit.qa.feature.domain.Part;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record ChecklistItemUpdateRequest(
        @Size(min = 1)
        @Schema(description = "행위")
        String title,

        @Size(min = 1)
        @Schema(description = "설명(세부 확인 내용)")
        String expected,

        @Size(min = 1)
        @Schema(description = "적용 파트. 빠진 파트의 셀은 NOT_APPLICABLE이 된다")
        Set<Part> parts,

        @Schema(description = "분류. AUTO 항목을 MANUAL(2차)로 옮길 때만 쓴다. MANUAL을 AUTO로 바꾸면 400")
        Classification classification,

        @NotNull
        @Schema(
                description = "체크 항목 낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Integer version
) {
}
