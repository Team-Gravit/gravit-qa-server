package gravit.qa.adapter.notion.support;

import gravit.qa.adapter.exception.InvalidNotionSpecException;
import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;
import gravit.qa.adapter.notion.dto.internal.NotionHistoryRowDto;
import gravit.qa.adapter.notion.dto.internal.NotionHistoryTableDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotionHistoryTableParser {

    public static final String HEADING = "수정 이력";
    public static final String SPRINT_COLUMN = "스프린트";
    public static final String CHANGE_COLUMN = "수정 사항";
    public static final String REFLECTED_COLUMN = "반영일";

    private static final String TABLE_ROW = "table_row";
    private static final String EMPTY_VALUE = "-";

    public static NotionHistoryTableDto parse(List<NotionBlockDto> blocks) {
        int headingIndex = NotionSections.findHeading(blocks, HEADING);
        if (headingIndex < 0) {
            return NotionHistoryTableDto.empty();
        }
        int tableIndex = NotionSections.findTableAfter(blocks, headingIndex);
        if (tableIndex < 0) {
            return NotionHistoryTableDto.empty();
        }

        NotionBlockDto table = blocks.get(tableIndex);
        List<NotionBlockDto> rows = table.children().stream()
                .filter(row -> TABLE_ROW.equals(row.type()))
                .toList();
        if (rows.isEmpty()) {
            return new NotionHistoryTableDto(blockId(blocks.get(headingIndex)), blockId(table), -1, List.of());
        }

        List<String> header = NotionSections.cells(rows.getFirst()).stream().map(String::trim).toList();
        int sprintIndex = requireColumn(header, SPRINT_COLUMN);
        int changeIndex = requireColumn(header, CHANGE_COLUMN);
        int reflectedIndex = requireColumn(header, REFLECTED_COLUMN);

        List<NotionHistoryRowDto> historyRows = new ArrayList<>();
        for (NotionBlockDto row : rows.subList(1, rows.size())) {
            List<String> cells = NotionSections.cells(row);
            String sprint = valueAt(cells, sprintIndex);
            String change = valueAt(cells, changeIndex);
            if (sprint == null && change == null) {
                continue;
            }
            historyRows.add(new NotionHistoryRowDto(blockId(row), sprint, change, valueAt(cells, reflectedIndex), cells));
        }
        return new NotionHistoryTableDto(blockId(blocks.get(headingIndex)), blockId(table), reflectedIndex, historyRows);
    }

    private static int requireColumn(
            List<String> header,
            String column
    ) {
        int index = header.indexOf(column);
        if (index < 0) {
            throw new InvalidNotionSpecException("수정 이력 표 머리글에 '" + column + "' 열이 없습니다");
        }
        return index;
    }

    private static String valueAt(
            List<String> cells,
            int index
    ) {
        if (index >= cells.size()) {
            return null;
        }
        String value = cells.get(index).trim();
        return value.isEmpty() || EMPTY_VALUE.equals(value) ? null : value;
    }

    private static String blockId(NotionBlockDto block) {
        return NotionIds.normalize(block.block().path("id").asText());
    }
}
