package gravit.qa.sprint.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record SetupJobStartResponse(
        @Schema(
                description = "시작한 작업 ID. GET /jobs/{jobId}로 진행을 본다",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long jobId
) {

    public static SetupJobStartResponse of(long jobId) {
        return new SetupJobStartResponse(jobId);
    }
}
