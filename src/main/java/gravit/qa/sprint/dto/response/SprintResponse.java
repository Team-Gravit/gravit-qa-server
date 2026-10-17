package gravit.qa.sprint.dto.response;

import gravit.qa.checklist.dto.response.PartProgressResponse;
import gravit.qa.global.util.KstTimes;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.SprintDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SprintResponse(
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
        List<PartProgressResponse> progress,

        @Schema(
                description = "Notion 스프린트 행 페이지 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String notionPageId,

        @Schema(description = "Figma 파일 키")
        String figmaFileKey,

        @Schema(description = "기준점. 준비 전이면 null")
        BaselineResponse baseline,

        @Schema(description = "마지막 셋업 작업 ID")
        Long latestSetupJobId,

        @Schema(description = "준비 완료 시각")
        OffsetDateTime preparedAt,

        @Schema(description = "분류 확정 시각")
        OffsetDateTime confirmedAt,

        @Schema(description = "종료 시각")
        OffsetDateTime closedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime createdAt,

        @Schema(
                description = "/admin/me의 adminId",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long createdBy,

        @Schema(
                description = "낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int version
) {

    public static SprintResponse of(
            SprintDto sprint,
            List<PartProgressResponse> progress,
            SprintGuideResponse guide,
            BaselineResponse baseline,
            Long latestSetupJobId
    ) {
        return new SprintResponse(
                guide,
                sprint.id(),
                sprint.sprintKey(),
                sprint.periodStart(),
                sprint.periodEnd(),
                sprint.status(),
                List.copyOf(progress),
                sprint.notionPageId(),
                sprint.figmaFileKey(),
                baseline,
                latestSetupJobId,
                KstTimes.toOffset(sprint.preparedAt()),
                KstTimes.toOffset(sprint.confirmedAt()),
                KstTimes.toOffset(sprint.closedAt()),
                KstTimes.toOffset(sprint.createdAt()),
                sprint.createdBy(),
                sprint.version()
        );
    }
}
