package gravit.qa.sprint.dto.response;

import gravit.qa.checklist.dto.response.PartProgressResponse;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.SprintDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record SprintSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SprintGuideResponse guide,

        @Schema(
                description = "스프린트 ID",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long id,

        @Schema(
                description = "Notion 스프린트 이름",
                example = "3rd Agile",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String sprintKey,

        @Schema(description = "기간 시작일")
        LocalDate periodStart,

        @Schema(description = "기간 종료일")
        LocalDate periodEnd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SprintStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PartProgressResponse> progress
) {

    public static SprintSummaryResponse of(
            SprintDto sprint,
            List<PartProgressResponse> progress,
            SprintGuideResponse guide
    ) {
        return new SprintSummaryResponse(
                guide,
                sprint.id(),
                sprint.sprintKey(),
                sprint.periodStart(),
                sprint.periodEnd(),
                sprint.status(),
                List.copyOf(progress)
        );
    }
}
