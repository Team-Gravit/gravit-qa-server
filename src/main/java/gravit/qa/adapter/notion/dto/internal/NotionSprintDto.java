package gravit.qa.adapter.notion.dto.internal;

import java.time.LocalDate;
import java.util.List;

public record NotionSprintDto(
        String pageId,
        String name,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<String> targetFeaturePageIds,
        String changeSummary,
        String figmaQaPageUrl,
        String qaBaseline,
        String rawJson
) {
}
