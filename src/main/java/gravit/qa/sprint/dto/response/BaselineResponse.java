package gravit.qa.sprint.dto.response;

import gravit.qa.global.util.KstTimes;
import gravit.qa.sprint.domain.Baseline;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record BaselineResponse(
        @Schema(
                description = "기준점 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long id,

        @Schema(description = "직전 종료 스프린트의 기준점 ID")
        Long previousBaselineId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<BaselineRepoResponse> repos
) {

    public static BaselineResponse of(
            Baseline baseline,
            List<BaselineRepoResponse> repos
    ) {
        return new BaselineResponse(
                baseline.getId(),
                baseline.getPreviousBaselineId(),
                KstTimes.toOffset(baseline.getCreatedAt()),
                List.copyOf(repos)
        );
    }
}
