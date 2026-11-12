package gravit.qa.fixReport.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record FixReportUncheckedResponse(
        @Schema(
                description = "디자인 검수 항목의 UNCHECKED 수",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long design,

        @Schema(
                description = "1차(분류 AUTO 항목) 체크 셀의 UNCHECKED 수",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long first,

        @Schema(
                description = "2차(분류 MANUAL 항목) 체크 셀의 UNCHECKED 수",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long second
) {

    public static FixReportUncheckedResponse of(
            long design,
            long first,
            long second
    ) {
        return new FixReportUncheckedResponse(design, first, second);
    }
}
