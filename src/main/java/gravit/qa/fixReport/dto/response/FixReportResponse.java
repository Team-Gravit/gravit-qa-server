package gravit.qa.fixReport.dto.response;

import gravit.qa.sprint.domain.SprintStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record FixReportResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long sprintId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SprintStatus status,

        @Schema(
                description = "파일을 내려받을 수 있는지. MANUAL_REVIEW이고 실행 중인 작업이 없고 미검수가 모두 0이거나, CLOSED이면 true",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean ready,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        FixReportUncheckedResponse unchecked,

        @Schema(
                description = "항상 4개. DESIGN, WEB, APP, SERVER 순서",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<FixReportPartResponse> parts
) {

    public static FixReportResponse of(
            long sprintId,
            SprintStatus status,
            boolean ready,
            FixReportUncheckedResponse unchecked,
            List<FixReportPartResponse> parts
    ) {
        return new FixReportResponse(sprintId, status, ready, unchecked, List.copyOf(parts));
    }
}
