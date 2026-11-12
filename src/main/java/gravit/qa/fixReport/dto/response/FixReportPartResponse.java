package gravit.qa.fixReport.dto.response;

import gravit.qa.sprint.domain.ProgressPart;
import io.swagger.v3.oas.annotations.media.Schema;

public record FixReportPartResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ProgressPart part,

        @Schema(
                description = "지금 FAIL 수. DESIGN은 디자인 항목, 나머지는 그 파트의 1차와 2차 셀 합계. ready와 관계없이 지금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long failCount,

        @Schema(
                description = "내려받을 파일 이름",
                example = "test-agile_SERVER_fixes.txt",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String fileName
) {

    public static FixReportPartResponse of(
            ProgressPart part,
            long failCount,
            String fileName
    ) {
        return new FixReportPartResponse(part, failCount, fileName);
    }
}
