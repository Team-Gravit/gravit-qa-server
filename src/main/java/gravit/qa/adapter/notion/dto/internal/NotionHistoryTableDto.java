package gravit.qa.adapter.notion.dto.internal;

import java.util.List;

public record NotionHistoryTableDto(
        String headingBlockId,
        String tableBlockId,
        int reflectedColumnIndex,
        List<NotionHistoryRowDto> rows
) {

    public static NotionHistoryTableDto empty() {
        return new NotionHistoryTableDto(null, null, -1, List.of());
    }

    public boolean exists() {
        return tableBlockId != null;
    }
}
