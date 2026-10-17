package gravit.qa.checklist.dto.request;

import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.domain.ReviewState;
import gravit.qa.feature.domain.Part;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

import java.util.Set;

public record ChecklistItemUpdateRequest(
        @Size(min = 1)
        @Schema(description = "검수 항목")
        String title,

        @Size(min = 1)
        @Schema(description = "기대 결과")
        String expected,

        @Size(min = 1)
        @Schema(description = "적용 파트. 빠진 파트의 셀은 NOT_APPLICABLE이 된다")
        Set<Part> parts,

        @Schema(description = "분류. AUTO면 testRef가 테스트 카탈로그에 있어야 한다")
        Classification classification,

        @Schema(
                implementation = String.class,
                nullable = true,
                description = "테스트 카탈로그 ID. 보내지 않으면 유지, null이면 해제"
        )
        JsonNullable<String> testRef,

        @Schema(
                implementation = String.class,
                nullable = true,
                description = "사전 조건 페르소나 키. 보내지 않으면 유지, null이면 해제"
        )
        JsonNullable<String> personaKey,

        @Schema(description = "검토 결과(CONFIRMED, REJECTED)")
        ReviewState reviewState,

        @NotNull
        @Schema(
                description = "체크 항목 낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Integer version
) {
}
