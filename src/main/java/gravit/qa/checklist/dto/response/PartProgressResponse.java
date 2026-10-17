package gravit.qa.checklist.dto.response;

import gravit.qa.feature.domain.Part;
import io.swagger.v3.oas.annotations.media.Schema;

public record PartProgressResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Part part,

        @Schema(
                description = "NOT_APPLICABLE 제외 셀 수",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long total,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long pass,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long fail,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long unchecked,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long notApplicable
) {

    public static PartProgressResponse of(
            Part part,
            long pass,
            long fail,
            long unchecked,
            long notApplicable
    ) {
        return new PartProgressResponse(part, pass + fail + unchecked, pass, fail, unchecked, notApplicable);
    }
}
