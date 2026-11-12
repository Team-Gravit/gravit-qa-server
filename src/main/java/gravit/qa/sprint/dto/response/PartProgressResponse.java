package gravit.qa.sprint.dto.response;

import gravit.qa.checklist.dto.internal.StateCountDto;
import gravit.qa.sprint.domain.ProgressPart;
import io.swagger.v3.oas.annotations.media.Schema;

public record PartProgressResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ProgressPart part,

        @Schema(
                description = "검수할 수 (NOT_APPLICABLE 제외). DESIGN은 디자인 항목, 나머지는 1차와 2차 셀 합계",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long total,

        @Schema(
                description = "UNCHECKED가 아닌 수",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long checked,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long pass,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long fail
) {

    public static PartProgressResponse of(
            ProgressPart part,
            StateCountDto count
    ) {
        return new PartProgressResponse(part, count.total(), count.checked(), count.pass(), count.fail());
    }
}
