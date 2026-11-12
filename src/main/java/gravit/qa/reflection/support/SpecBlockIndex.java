package gravit.qa.reflection.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;
import gravit.qa.adapter.notion.dto.internal.NotionHistoryTableDto;
import gravit.qa.adapter.notion.support.NotionIds;
import gravit.qa.adapter.notion.support.NotionRichTexts;
import gravit.qa.adapter.notion.support.NotionSections;
import gravit.qa.adapter.notion.support.NotionWriteGuard;
import gravit.qa.reflection.domain.ReflectionOperationType;
import gravit.qa.reflection.dto.internal.ReflectionOperationDto;
import gravit.qa.reflection.dto.internal.SpecBlockEntryDto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SpecBlockIndex {

    private static final String TABLE = "table";
    private static final String TABLE_ROW = "table_row";
    private static final Set<String> UNEXPANDED_TYPES = Set.of("child_page", "child_database");
    private static final String BLOCK_KEY = "b%d";
    private static final String TABLE_KEY = "t%d";
    private static final String ROW_KEY = "%s.r%d";
    private static final String INDENT = "  ";
    private static final String CELL_DELIMITER = " | ";
    private static final String LINE_BREAK = "\n";
    private static final String NEW_BLOCK_ID = "new-%d";

    private final String pageId;
    private final List<NotionBlockDto> blocks;
    private final Map<String, SpecBlockEntryDto> keyToEntry = new LinkedHashMap<>();
    private final Map<String, SpecBlockEntryDto> blockIdToEntry = new LinkedHashMap<>();
    private final Set<String> excludedIds;
    private int blockCount;
    private int tableCount;

    private SpecBlockIndex(
            String pageId,
            List<NotionBlockDto> blocks,
            NotionHistoryTableDto history
    ) {
        this.pageId = NotionIds.normalize(pageId);
        this.blocks = List.copyOf(blocks);
        this.excludedIds = history.exists() ? Set.of(history.headingBlockId(), history.tableBlockId()) : Set.of();
        index(blocks, this.pageId, 0, true);
    }

    public static SpecBlockIndex of(
            String pageId,
            List<NotionBlockDto> blocks,
            NotionHistoryTableDto history
    ) {
        return new SpecBlockIndex(pageId, blocks, history);
    }

    public String pageId() {
        return pageId;
    }

    public Optional<SpecBlockEntryDto> find(String key) {
        return Optional.ofNullable(key == null ? null : keyToEntry.get(key.trim()));
    }

    public Optional<SpecBlockEntryDto> findByBlockId(String blockId) {
        return Optional.ofNullable(blockId == null ? null : blockIdToEntry.get(NotionIds.normalize(blockId)));
    }

    public List<SpecBlockEntryDto> entries() {
        return List.copyOf(keyToEntry.values());
    }

    public String render() {
        List<String> lines = new ArrayList<>();
        for (SpecBlockEntryDto entry : keyToEntry.values()) {
            lines.add(INDENT.repeat(entry.depth()) + "[" + entry.key() + "] " + describe(entry));
        }
        return String.join(LINE_BREAK, lines);
    }

    public List<NotionBlockDto> apply(List<ReflectionOperationDto> operations) {
        return new Applier(operations).applyTo(blocks);
    }

    private void index(
            List<NotionBlockDto> children,
            String parentId,
            int depth,
            boolean topLevel
    ) {
        for (int position = 0; position < children.size(); position++) {
            NotionBlockDto block = children.get(position);
            String blockId = idOf(block);
            if (excludedIds.contains(blockId)) {
                continue;
            }

            if (TABLE.equals(block.type())) {
                indexTable(block, blockId, parentId, depth, topLevel && position == 0);
                continue;
            }

            String type = block.type();
            boolean editable = NotionWriteGuard.TEXT_BLOCK_TYPES.contains(type);
            String text = editable ? NotionRichTexts.plainText(block.content().path("rich_text")) : null;
            put(new SpecBlockEntryDto(BLOCK_KEY.formatted(++blockCount), blockId, type, parentId, depth, editable, false, false, null, text, null, editedTime(block)));
            if (!UNEXPANDED_TYPES.contains(type)) {
                index(block.children(), blockId, depth + 1, false);
            }
        }
    }

    private void indexTable(
            NotionBlockDto table,
            String tableId,
            String parentId,
            int depth,
            boolean basicTable
    ) {
        int width = table.content().path("table_width").asInt();
        String tableKey = TABLE_KEY.formatted(++tableCount);
        put(new SpecBlockEntryDto(tableKey, tableId, TABLE, parentId, depth, false, false, basicTable, width, null, null, editedTime(table)));

        int rowIndex = 0;
        for (NotionBlockDto row : table.children()) {
            if (!TABLE_ROW.equals(row.type())) {
                continue;
            }
            rowIndex++;
            boolean header = rowIndex == 1;
            put(new SpecBlockEntryDto(
                    ROW_KEY.formatted(tableKey, rowIndex),
                    idOf(row),
                    TABLE_ROW,
                    tableId,
                    depth + 1,
                    !header,
                    header,
                    basicTable,
                    width,
                    null,
                    NotionSections.cells(row),
                    editedTime(row)
            ));
        }
    }

    private void put(SpecBlockEntryDto entry) {
        keyToEntry.put(entry.key(), entry);
        blockIdToEntry.put(entry.blockId(), entry);
    }

    private String describe(SpecBlockEntryDto entry) {
        if (entry.isTable()) {
            return "table (열 " + entry.tableWidth() + "개)" + (entry.basicTable() ? " 명세 맨 위 항목, 값 표: 값 칸만 수정, 행 추가 금지" : "");
        }
        if (entry.isRow()) {
            String cells = String.join(CELL_DELIMITER, entry.cells().stream().map(cell -> cell.replace(LINE_BREAK, "<br>")).toList());
            return (entry.headerRow() ? "(머리글, 수정 금지) " : "") + cells;
        }
        if (!entry.editable()) {
            return entry.type() + " (수정 금지)";
        }
        return entry.type() + ": " + entry.text().replace(LINE_BREAK, "<br>");
    }

    private static String idOf(NotionBlockDto block) {
        return NotionIds.normalize(block.block().path("id").asText());
    }

    private static String editedTime(NotionBlockDto block) {
        return block.block().path("last_edited_time").asText(null);
    }

    private static final class Applier {

        private final Map<String, ReflectionOperationDto> blockIdToUpdate = new LinkedHashMap<>();
        private final Set<String> deletedIds = new HashSet<>();
        private final Map<String, List<ReflectionOperationDto>> targetIdToInserts = new LinkedHashMap<>();
        private int newBlockCount;

        private Applier(List<ReflectionOperationDto> operations) {
            for (ReflectionOperationDto operation : operations) {
                switch (operation.type()) {
                    case UPDATE_TEXT, UPDATE_ROW -> blockIdToUpdate.put(operation.targetBlockId(), operation);
                    case DELETE -> deletedIds.add(operation.targetBlockId());
                    case INSERT_BLOCK_AFTER, INSERT_ROW_AFTER -> targetIdToInserts
                            .computeIfAbsent(operation.targetBlockId(), id -> new ArrayList<>())
                            .add(operation);
                }
            }
        }

        private List<NotionBlockDto> applyTo(List<NotionBlockDto> source) {
            List<NotionBlockDto> result = new ArrayList<>();
            for (NotionBlockDto block : source) {
                String blockId = idOf(block);
                if (!deletedIds.contains(blockId)) {
                    result.add(new NotionBlockDto(updated(block, blockIdToUpdate.get(blockId)), applyTo(block.children())));
                }
                for (ReflectionOperationDto insert : targetIdToInserts.getOrDefault(blockId, List.of())) {
                    result.add(new NotionBlockDto(newBlock(insert), List.of()));
                }
            }
            return result;
        }

        private JsonNode updated(
                NotionBlockDto block,
                ReflectionOperationDto update
        ) {
            if (update == null) {
                return block.block();
            }
            ObjectNode copy = block.block().deepCopy();
            if (update.type() == ReflectionOperationType.UPDATE_TEXT) {
                ((ObjectNode) copy.path(block.type())).set("rich_text", displayText(update.text()));
            } else {
                ((ObjectNode) copy.path(TABLE_ROW)).set("cells", displayCells(update.cells()));
            }
            return copy;
        }

        private ObjectNode newBlock(ReflectionOperationDto insert) {
            ObjectNode block = JsonNodeFactory.instance.objectNode()
                    .put("id", NEW_BLOCK_ID.formatted(++newBlockCount))
                    .put("has_children", false);
            if (insert.type() == ReflectionOperationType.INSERT_ROW_AFTER) {
                block.put("type", TABLE_ROW);
                block.putObject(TABLE_ROW).set("cells", displayCells(insert.cells()));
            } else {
                block.put("type", insert.blockType());
                block.putObject(insert.blockType()).set("rich_text", displayText(insert.text()));
            }
            return block;
        }

        private static ArrayNode displayText(String text) {
            ArrayNode richText = JsonNodeFactory.instance.arrayNode();
            richText.addObject().put("plain_text", text == null ? "" : text);
            return richText;
        }

        private static ArrayNode displayCells(List<String> cells) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            cells.forEach(cell -> array.add(displayText(cell)));
            return array;
        }
    }
}
