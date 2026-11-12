package gravit.qa.sprint.dto.response;

import gravit.qa.sprint.domain.ConnectionTarget;
import gravit.qa.sprint.dto.internal.ConnectionResultDto;
import io.swagger.v3.oas.annotations.media.Schema;

public record ConnectionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ConnectionTarget target,

        @Schema(
                description = "마지막 준비 작업에서 읽기에 성공했는지",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean connected,

        @Schema(
                description = "저장소면 '{브랜치}@{짧은 SHA}', Notion과 Figma는 null",
                example = "qa-baseline@0eced9c"
        )
        String ref,

        @Schema(description = "실패 사유 한 줄. 성공이면 null")
        String message
) {

    public static ConnectionResponse from(ConnectionResultDto connection) {
        return new ConnectionResponse(connection.target(), connection.connected(), connection.ref(), connection.message());
    }
}
