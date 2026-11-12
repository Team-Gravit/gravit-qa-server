package gravit.qa.sprint.dto.response;

import gravit.qa.job.dto.response.JobResponse;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.SprintDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SprintResponse(
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
        SprintStatus status,

        @Schema(
                description = "낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int version,

        @Schema(
                description = "연결 상태. 준비 작업 전이면 빈 배열",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<ConnectionResponse> connections,

        @Schema(
                description = "파트별 진행도(DESIGN, WEB, APP, SERVER)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<PartProgressResponse> progress,

        @Schema(
                description = "준비 작업 5단계",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<SetupStepResponse> setupSteps,

        @Schema(description = "이 스프린트의 마지막 작업. 없으면 null")
        JobResponse latestJob,

        @Schema(
                description = "처리하지 않은 수정 이력 반영 제안 수(PENDING, FAILED, APPROVED)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long pendingReflectionCount
) {

    public static SprintResponse of(
            SprintDto sprint,
            List<ConnectionResponse> connections,
            List<PartProgressResponse> progress,
            List<SetupStepResponse> setupSteps,
            JobResponse latestJob,
            long pendingReflectionCount
    ) {
        return new SprintResponse(
                sprint.id(),
                sprint.sprintKey(),
                SprintSummaryResponse.summaryOf(sprint.changeSummary()),
                sprint.status(),
                sprint.version(),
                List.copyOf(connections),
                List.copyOf(progress),
                List.copyOf(setupSteps),
                latestJob,
                pendingReflectionCount
        );
    }
}
