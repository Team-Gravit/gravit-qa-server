package gravit.qa.adapter.notion.support;

import com.fasterxml.jackson.databind.JsonNode;
import gravit.qa.adapter.domain.ExternalApi;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.notion.dto.internal.NotionWriteScopeDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpMethod;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotionWriteGuard {

    public static final Set<String> TEXT_BLOCK_TYPES = Set.of(
            "paragraph",
            "heading_1",
            "heading_2",
            "heading_3",
            "bulleted_list_item",
            "numbered_list_item",
            "to_do",
            "quote",
            "callout",
            "toggle"
    );
    public static final String TABLE_ROW = "table_row";

    private static final String SEARCH_PATH = "/v1/search";
    private static final Pattern DATABASE_QUERY_PATH = Pattern.compile("^/v1/databases/[0-9a-fA-F-]{32,36}/query$");
    private static final Pattern BLOCK_PATH = Pattern.compile("^/v1/blocks/([0-9a-fA-F-]{32,36})$");
    private static final Pattern BLOCK_CHILDREN_PATH = Pattern.compile("^/v1/blocks/([0-9a-fA-F-]{32,36})/children$");
    private static final Pattern REFLECTED_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final String RICH_TEXT_FIELD = "rich_text";
    private static final String CHILDREN_FIELD = "children";
    private static final String AFTER_FIELD = "after";
    private static final String CELLS_FIELD = "cells";
    private static final String TYPE_FIELD = "type";
    private static final int MAX_APPEND_CHILDREN = 100;

    public static void check(
            HttpMethod method,
            String path,
            JsonNode body,
            NotionWriteScopeDto scope
    ) {
        if (!isAllowed(method, path, body, scope)) {
            throw new NonRetryableExternalApiException(ExternalApi.NOTION, null, "허용 목록에 없는 Notion 요청을 차단했습니다: " + method + " " + path);
        }
    }

    public static boolean isAllowed(
            HttpMethod method,
            String path,
            JsonNode body,
            NotionWriteScopeDto scope
    ) {
        if (HttpMethod.GET.equals(method)) {
            return true;
        }
        if (HttpMethod.POST.equals(method)) {
            return SEARCH_PATH.equals(path) || DATABASE_QUERY_PATH.matcher(path).matches();
        }
        if (scope == null) {
            return false;
        }

        Matcher children = BLOCK_CHILDREN_PATH.matcher(path);
        if (HttpMethod.PATCH.equals(method) && children.matches()) {
            return isAllowedAppend(NotionIds.normalize(children.group(1)), body, scope);
        }
        Matcher block = BLOCK_PATH.matcher(path);
        if (HttpMethod.PATCH.equals(method) && block.matches()) {
            return isAllowedUpdate(NotionIds.normalize(block.group(1)), body, scope);
        }
        if (HttpMethod.DELETE.equals(method) && block.matches()) {
            return body == null && scope.blockIdToType().containsKey(NotionIds.normalize(block.group(1)));
        }
        return false;
    }

    private static boolean isAllowedUpdate(
            String blockId,
            JsonNode body,
            NotionWriteScopeDto scope
    ) {
        if (body == null || !body.isObject() || body.size() != 1) {
            return false;
        }
        String field = body.fieldNames().next();
        JsonNode content = body.get(field);

        if (TABLE_ROW.equals(field)) {
            if (scope.historyRowIdToCells().containsKey(blockId)) {
                return isReflectedDateOnly(content, scope.historyRowIdToCells().get(blockId), scope.reflectedColumnIndex());
            }
            Integer width = scope.rowIdToWidth().get(blockId);
            return width != null && hasOnly(content, CELLS_FIELD) && isCells(content.get(CELLS_FIELD), width);
        }
        return TEXT_BLOCK_TYPES.contains(field)
                && field.equals(scope.blockIdToType().get(blockId))
                && hasOnly(content, RICH_TEXT_FIELD)
                && content.get(RICH_TEXT_FIELD).isArray();
    }

    private static boolean isAllowedAppend(
            String parentId,
            JsonNode body,
            NotionWriteScopeDto scope
    ) {
        if (body == null || !body.isObject() || !body.has(CHILDREN_FIELD)) {
            return false;
        }
        Iterator<String> fields = body.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!CHILDREN_FIELD.equals(field) && !AFTER_FIELD.equals(field)) {
                return false;
            }
        }
        if (body.has(AFTER_FIELD) && !scope.isKnownBlock(NotionIds.normalize(body.get(AFTER_FIELD).asText()))) {
            return false;
        }

        JsonNode children = body.get(CHILDREN_FIELD);
        if (!children.isArray() || children.isEmpty() || children.size() > MAX_APPEND_CHILDREN) {
            return false;
        }
        Integer tableWidth = scope.tableIdToWidth().get(parentId);
        if (tableWidth == null && !scope.containerIds().contains(parentId)) {
            return false;
        }
        for (JsonNode child : children) {
            if (!(tableWidth == null ? isTextBlock(child) : isTableRow(child, tableWidth))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTextBlock(JsonNode child) {
        String type = child.path(TYPE_FIELD).asText();
        return TEXT_BLOCK_TYPES.contains(type)
                && hasOnlyFields(child, TYPE_FIELD, type)
                && hasOnly(child.get(type), RICH_TEXT_FIELD);
    }

    private static boolean isTableRow(
            JsonNode child,
            int width
    ) {
        return TABLE_ROW.equals(child.path(TYPE_FIELD).asText())
                && hasOnlyFields(child, TYPE_FIELD, TABLE_ROW)
                && hasOnly(child.get(TABLE_ROW), CELLS_FIELD)
                && isCells(child.get(TABLE_ROW).get(CELLS_FIELD), width);
    }

    private static boolean isReflectedDateOnly(
            JsonNode content,
            List<String> currentCells,
            int reflectedColumnIndex
    ) {
        if (!hasOnly(content, CELLS_FIELD) || !isCells(content.get(CELLS_FIELD), currentCells.size())) {
            return false;
        }
        JsonNode cells = content.get(CELLS_FIELD);
        for (int index = 0; index < currentCells.size(); index++) {
            String text = NotionRichTexts.plainText(cells.get(index)).trim();
            if (index == reflectedColumnIndex) {
                if (!REFLECTED_DATE.matcher(text).matches()) {
                    return false;
                }
            } else if (!text.equals(currentCells.get(index).trim())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCells(
            JsonNode cells,
            int width
    ) {
        if (cells == null || !cells.isArray() || cells.size() != width) {
            return false;
        }
        for (JsonNode cell : cells) {
            if (!cell.isArray()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOnly(
            JsonNode node,
            String field
    ) {
        return node != null && node.isObject() && node.size() == 1 && node.has(field);
    }

    private static boolean hasOnlyFields(
            JsonNode node,
            String first,
            String second
    ) {
        return node != null && node.isObject() && node.size() == 2 && node.has(first) && node.has(second);
    }
}
