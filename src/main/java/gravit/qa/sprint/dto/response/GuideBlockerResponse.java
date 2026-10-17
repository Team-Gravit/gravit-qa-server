package gravit.qa.sprint.dto.response;

import gravit.qa.feature.domain.Part;
import gravit.qa.sprint.domain.GuideBlockerCode;
import io.swagger.v3.oas.annotations.media.Schema;

public record GuideBlockerResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        GuideBlockerCode code,

        @Schema(description = "남은 수(해당 시)")
        Long count,

        @Schema(description = "파트(파트별 막힌 이유일 때)")
        Part part
) {

    public static GuideBlockerResponse of(
            GuideBlockerCode code,
            Long count,
            Part part
    ) {
        return new GuideBlockerResponse(code, count, part);
    }
}
