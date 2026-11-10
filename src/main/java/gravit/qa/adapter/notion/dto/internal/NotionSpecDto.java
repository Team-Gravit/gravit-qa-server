package gravit.qa.adapter.notion.dto.internal;

import java.time.LocalDate;

public record NotionSpecDto(
        String pageId,
        String screenId,
        String name,
        LocalDate lastVerifiedAt,
        String rawJson
) {
}
