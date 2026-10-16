package gravit.qa.adapter.notion.dto.internal;

import java.util.List;

public record NotionPageBodyDto(
        String pageId,
        List<NotionBlockDto> blocks,
        String markdown,
        String rawJson
) {
}
