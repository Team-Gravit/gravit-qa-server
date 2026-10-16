package gravit.qa.job.dto.response;

import gravit.qa.global.util.KstTimes;
import gravit.qa.job.domain.JobStepStatus;
import gravit.qa.job.dto.internal.JobStepDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record JobStepResponse(
        @Schema(
                description = "단계 순서",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int seq,

        @Schema(
                description = "단계 이름",
                example = "BASELINE",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        JobStepStatus status,

        @Schema(description = "실패·건너뜀 사유")
        String message,

        @Schema(description = "시작 시각")
        OffsetDateTime startedAt,

        @Schema(description = "종료 시각")
        OffsetDateTime finishedAt
) {

    public static JobStepResponse from(JobStepDto step) {
        return new JobStepResponse(
                step.seq(),
                step.name(),
                step.status(),
                step.message(),
                KstTimes.toOffset(step.startedAt()),
                KstTimes.toOffset(step.finishedAt())
        );
    }
}
