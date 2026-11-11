package gravit.qa.adapter.notion.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import gravit.qa.adapter.exception.InvalidNotionSpecException;
import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecTableDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotionSpecTableParser {

    public static final String STATUS_ITEM = "명세 상태";
    public static final String PARTS_ITEM = "파트";
    public static final String ROUTE_ITEM = "라우트";
    public static final String RELATED_API_ITEM = "관련 API";
    public static final String FIGMA_ITEM = "Figma";
    public static final String FE_SPEC_ID_ITEM = "FE 스펙 ID";

    private static final String TABLE = "table";
    private static final String TABLE_ROW = "table_row";
    private static final String CELLS_FIELD = "cells";
    private static final String PLAIN_TEXT_FIELD = "plain_text";
    private static final String HREF_FIELD = "href";
    private static final String EMPTY_VALUE = "-";
    private static final String SPACE = " ";
    private static final Pattern ITEM_DELIMITER = Pattern.compile(",");
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");
    private static final Pattern LIST_MARKER = Pattern.compile("^[\\s•\\-]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int KEY_CELL = 0;
    private static final int VALUE_CELL = 1;

    public static NotionSpecTableDto parse(List<NotionBlockDto> blocks) {
        if (blocks.isEmpty() || !TABLE.equals(blocks.getFirst().type())) {
            throw new InvalidNotionSpecException("명세 맨 위에 항목, 값 표가 없습니다(본문 첫 블록이 표가 아님)");
        }

        Map<String, JsonNode> itemToCell = itemToCell(blocks.getFirst());
        String specStatus = value(cellOf(itemToCell, STATUS_ITEM));
        if (specStatus == null) {
            throw new InvalidNotionSpecException("명세 맨 위 표의 '" + STATUS_ITEM + "' 칸이 비어 있습니다");
        }

        return NotionSpecTableDto.of(
                specStatus,
                items(cellOf(itemToCell, PARTS_ITEM)),
                value(cellOf(itemToCell, ROUTE_ITEM)),
                lines(cellOf(itemToCell, RELATED_API_ITEM)),
                href(cellOf(itemToCell, FIGMA_ITEM)),
                items(cellOf(itemToCell, FE_SPEC_ID_ITEM))
        );
    }

    private static Map<String, JsonNode> itemToCell(NotionBlockDto table) {
        Map<String, JsonNode> itemToCell = new LinkedHashMap<>();
        for (NotionBlockDto row : table.children()) {
            if (!TABLE_ROW.equals(row.type())) {
                continue;
            }
            JsonNode cells = row.content().path(CELLS_FIELD);
            String item = plainText(cells.path(KEY_CELL)).trim();
            itemToCell.putIfAbsent(item, cells.path(VALUE_CELL));
        }
        return itemToCell;
    }

    private static JsonNode cellOf(
            Map<String, JsonNode> itemToCell,
            String item
    ) {
        return itemToCell.getOrDefault(item, MissingNode.getInstance());
    }

    private static String value(JsonNode cell) {
        String text = plainText(cell).trim();
        return text.isEmpty() || EMPTY_VALUE.equals(text) ? null : text;
    }

    private static List<String> items(JsonNode cell) {
        String value = value(cell);
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(ITEM_DELIMITER.split(value))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static List<String> lines(JsonNode cell) {
        String value = value(cell);
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(LINE_BREAK.split(value))
                .map(line -> LIST_MARKER.matcher(line).replaceFirst(""))
                .map(line -> WHITESPACE.matcher(line).replaceAll(SPACE).trim())
                .filter(line -> !line.isEmpty())
                .distinct()
                .toList();
    }

    private static String href(JsonNode cell) {
        for (JsonNode item : cell) {
            String href = item.path(HREF_FIELD).asText(null);
            if (href != null && !href.isBlank()) {
                return href;
            }
        }
        return null;
    }

    private static String plainText(JsonNode cell) {
        StringBuilder builder = new StringBuilder();
        cell.forEach(item -> builder.append(item.path(PLAIN_TEXT_FIELD).asText()));
        return builder.toString();
    }
}
