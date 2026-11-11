package gravit.qa.adapter.notion.support;

import com.fasterxml.jackson.databind.JsonNode;
import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotionSections {

    private static final Set<String> HEADING_TYPES = Set.of("heading_1", "heading_2", "heading_3");
    private static final String TABLE = "table";
    private static final String RICH_TEXT_FIELD = "rich_text";
    private static final String CELLS_FIELD = "cells";

    public static boolean isHeading(NotionBlockDto block) {
        return HEADING_TYPES.contains(block.type());
    }

    public static String headingText(NotionBlockDto block) {
        return NotionRichTexts.plainText(block.content().path(RICH_TEXT_FIELD)).trim();
    }

    public static int findHeading(
            List<NotionBlockDto> blocks,
            String title
    ) {
        for (int index = 0; index < blocks.size(); index++) {
            NotionBlockDto block = blocks.get(index);
            if (isHeading(block) && title.equals(headingText(block))) {
                return index;
            }
        }
        return -1;
    }

    public static int findTableAfter(
            List<NotionBlockDto> blocks,
            int headingIndex
    ) {
        for (int index = headingIndex + 1; index < blocks.size(); index++) {
            NotionBlockDto block = blocks.get(index);
            if (TABLE.equals(block.type())) {
                return index;
            }
            if (isHeading(block)) {
                return -1;
            }
        }
        return -1;
    }

    public static List<String> cells(NotionBlockDto row) {
        List<String> cells = new ArrayList<>();
        for (JsonNode cell : row.content().path(CELLS_FIELD)) {
            cells.add(NotionRichTexts.plainText(cell));
        }
        return cells;
    }
}
