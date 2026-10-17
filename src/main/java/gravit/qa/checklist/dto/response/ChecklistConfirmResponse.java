package gravit.qa.checklist.dto.response;

import gravit.qa.run.dto.response.AutoRunSummaryResponse;
import gravit.qa.sprint.dto.response.SprintResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChecklistConfirmResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SprintResponse sprint,

        @Schema(description = "확정과 함께 큐에 넣은 자동 실행. 없으면 null")
        AutoRunSummaryResponse autoRun
) {

    public static ChecklistConfirmResponse of(
            SprintResponse sprint,
            AutoRunSummaryResponse autoRun
    ) {
        return new ChecklistConfirmResponse(sprint, autoRun);
    }
}
