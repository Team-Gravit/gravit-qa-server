package gravit.qa.sprint.dto.response;

import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.SprintDto;
import io.swagger.v3.oas.annotations.media.Schema;

public record SprintSummaryResponse(
        @Schema(
                description = "스프린트 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long id,

        @Schema(
                description = "Notion 스프린트 이름",
                example = "3rd Agile",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String sprintKey,

        @Schema(description = "Notion 스프린트 '변경 요약'. 비어 있으면 null")
        String summary,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SprintStatus status
) {

    public static SprintSummaryResponse from(SprintDto sprint) {
        return new SprintSummaryResponse(sprint.id(), sprint.sprintKey(), summaryOf(sprint.changeSummary()), sprint.status());
    }

    static String summaryOf(String changeSummary) {
        return changeSummary == null || changeSummary.isBlank() ? null : changeSummary.trim();
    }
}
