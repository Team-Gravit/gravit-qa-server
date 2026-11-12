package gravit.qa.adapter.notion.support;

import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;
import gravit.qa.adapter.notion.dto.internal.NotionHistoryTableDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecRowDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotionSpecBodyParser {

    public static final List<String> SECTION_TITLES = List.of("화면에 보이는 것", "사용자가 할 수 있는 것", "예외 상황");

    private static final Pattern ROW_ID = Pattern.compile("^[A-Z]{1,3}\\d+$");
    private static final String TABLE_ROW = "table_row";

    public static List<NotionSpecRowDto> parseRows(List<NotionBlockDto> blocks) {
        List<NotionSpecRowDto> rows = new ArrayList<>();
        for (String title : SECTION_TITLES) {
            int headingIndex = NotionSections.findHeading(blocks, title);
            if (headingIndex < 0) {
                continue;
            }
            int tableIndex = NotionSections.findTableAfter(blocks, headingIndex);
            if (tableIndex < 0) {
                continue;
            }
            rows.addAll(rowsOf(title, blocks.get(tableIndex)));
        }
        return rows;
    }

    public static List<NotionBlockDto> specBlocks(
            List<NotionBlockDto> blocks,
            NotionHistoryTableDto history
    ) {
        List<NotionBlockDto> specBlocks = new ArrayList<>();
        for (int index = 1; index < blocks.size(); index++) {
            NotionBlockDto block = blocks.get(index);
            String blockId = NotionIds.normalize(block.block().path("id").asText());
            if (blockId.equals(history.headingBlockId()) || blockId.equals(history.tableBlockId())) {
                continue;
            }
            specBlocks.add(block);
        }
        return specBlocks;
    }

    private static List<NotionSpecRowDto> rowsOf(
            String title,
            NotionBlockDto table
    ) {
        List<NotionBlockDto> tableRows = table.children().stream()
                .filter(row -> TABLE_ROW.equals(row.type()))
                .toList();
        if (tableRows.isEmpty()) {
            return List.of();
        }

        List<String> columns = NotionSections.cells(tableRows.getFirst()).stream().map(String::trim).toList();
        List<NotionSpecRowDto> rows = new ArrayList<>();
        for (NotionBlockDto row : tableRows.subList(1, tableRows.size())) {
            List<String> cells = NotionSections.cells(row).stream().map(String::trim).toList();
            if (cells.isEmpty() || !ROW_ID.matcher(cells.getFirst()).matches()) {
                continue;
            }
            rows.add(new NotionSpecRowDto(NotionIds.normalize(row.block().path("id").asText()), title, cells.getFirst(), columns, cells));
        }
        return rows;
    }
}
