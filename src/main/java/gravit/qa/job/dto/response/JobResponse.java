package gravit.qa.job.dto.response;

import gravit.qa.global.util.KstTimes;
import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.dto.internal.JobDto;
import gravit.qa.job.dto.internal.JobStepDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record JobResponse(
        @Schema(
                description = "작업 ID",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        JobType type,

        @Schema(description = "스프린트 ID")
        Long sprintId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        JobStatus status,

        @Schema(
                description = "현재까지 시도 횟수",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int attempt,

        @Schema(
                description = "최대 시도 횟수(최초 1회 + 재시도)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int maxAttempts,

        @Schema(description = "대기 중인 작업의 다음 실행 시각")
        OffsetDateTime nextRunAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean cancelRequested,

        @Schema(description = "마지막 실패 사유")
        String lastError,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<JobStepResponse> steps,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime createdAt,

        @Schema(description = "종료 시각")
        OffsetDateTime finishedAt
) {

    public static JobResponse of(
            JobDto job,
            List<JobStepDto> steps
    ) {
        return new JobResponse(
                job.id(),
                job.type(),
                job.sprintId(),
                job.status(),
                job.attempt(),
                job.maxAttempts(),
                job.status() == JobStatus.QUEUED ? KstTimes.toOffset(job.runAfter()) : null,
                job.cancelRequested(),
                job.lastError(),
                steps.stream().map(JobStepResponse::from).toList(),
                KstTimes.toOffset(job.createdAt()),
                KstTimes.toOffset(job.finishedAt())
        );
    }
}
