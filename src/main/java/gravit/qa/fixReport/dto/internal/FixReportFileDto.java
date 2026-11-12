package gravit.qa.fixReport.dto.internal;

public record FixReportFileDto(
        String fileName,
        String content
) {

    public static FixReportFileDto of(
            String fileName,
            String content
    ) {
        return new FixReportFileDto(fileName, content);
    }
}
