package gravit.qa.fixReport.dto.internal;

import gravit.qa.designReview.domain.DesignPlatform;

import java.util.List;

public record FixReportScreenDto(
        long featureId,
        String screenId,
        String screenName,
        String notionPageId,
        DesignPlatform platform,
        List<FixReportEntryDto> entries
) {

    public static FixReportScreenDto of(
            long featureId,
            String screenId,
            String screenName,
            String notionPageId,
            DesignPlatform platform,
            List<FixReportEntryDto> entries
    ) {
        return new FixReportScreenDto(featureId, screenId, screenName, notionPageId, platform, List.copyOf(entries));
    }
}
