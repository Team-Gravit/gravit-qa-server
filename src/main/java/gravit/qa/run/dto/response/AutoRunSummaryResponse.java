package gravit.qa.run.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import gravit.qa.global.util.KstTimes;
import gravit.qa.run.domain.AutoRun;
import gravit.qa.run.domain.RunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record AutoRunSummaryResponse(
        @Schema(
                description = "자동 실행 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long id,

        @Schema(
                description = "스프린트 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long sprintId,

        @Schema(description = "자동 실행 작업 ID")
        long jobId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        RunStatus status,

        @JsonRawValue
        @Schema(description = "케이스 결과 집계(RunCounts). 실행 전이면 null")
        String counts,

        @Schema(description = "실행 자체 실패 사유")
        String failureReason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime createdAt,

        @Schema(description = "시작 시각")
        OffsetDateTime startedAt,

        @Schema(description = "종료 시각")
        OffsetDateTime finishedAt
) {

    public static AutoRunSummaryResponse from(AutoRun autoRun) {
        return new AutoRunSummaryResponse(
                autoRun.getId(),
                autoRun.getSprintId(),
                autoRun.getJobId(),
                autoRun.getStatus(),
                autoRun.getCounts(),
                autoRun.getFailureReason(),
                KstTimes.toOffset(autoRun.getCreatedAt()),
                KstTimes.toOffset(autoRun.getStartedAt()),
                KstTimes.toOffset(autoRun.getFinishedAt())
        );
    }
}
