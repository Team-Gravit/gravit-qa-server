package gravit.qa.sprint.dto.response;

import gravit.qa.sprint.domain.GuideAction;
import gravit.qa.sprint.domain.GuideStage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SprintGuideResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        GuideStage stage,

        @Schema(
                description = "현재 단계 위치(1부터)",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int stageIndex,

        @Schema(
                description = "전체 단계 수",
                example = "5",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int stageCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        GuideAction nextAction,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<GuideBlockerResponse> blockers
) {

    public static SprintGuideResponse of(
            GuideStage stage,
            int stageCount,
            GuideAction nextAction,
            List<GuideBlockerResponse> blockers
    ) {
        return new SprintGuideResponse(stage, stage.getIndex(), stageCount, nextAction, List.copyOf(blockers));
    }
}
