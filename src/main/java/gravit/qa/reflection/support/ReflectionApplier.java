package gravit.qa.reflection.support;

import gravit.qa.adapter.notion.dto.internal.NotionHistoryRowDto;
import gravit.qa.adapter.notion.dto.internal.NotionHistoryTableDto;
import gravit.qa.adapter.notion.dto.internal.NotionPageBodyDto;
import gravit.qa.adapter.notion.dto.internal.NotionWriteScopeDto;
import gravit.qa.adapter.notion.infrastructure.NotionClient;
import gravit.qa.adapter.notion.support.NotionHistoryTableParser;
import gravit.qa.adapter.notion.support.NotionWriteGuard;
import gravit.qa.global.util.TimeZoneConst;
import gravit.qa.reflection.dto.internal.ReflectionHistoryRowDto;
import gravit.qa.reflection.dto.internal.ReflectionOperationDto;
import gravit.qa.reflection.dto.internal.SpecBlockEntryDto;
import gravit.qa.reflection.dto.internal.SpecReflectionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

@Component
@RequiredArgsConstructor
public class ReflectionApplier {

    private static final String TABLE_ROW = "table_row";
    private static final String SPEC_CHANGED = "제안을 만든 뒤 Notion 명세가 바뀌었습니다: ";

    private final NotionClient notionClient;

    private final Clock clock;

    public void apply(
            SpecReflectionDto reflection,
            String pageId,
            BiConsumer<Integer, String> onApplied
    ) {
        NotionPageBodyDto body = notionClient.getPageBody(pageId);
        NotionHistoryTableDto history = NotionHistoryTableParser.parse(body.blocks());
        SpecBlockIndex index = SpecBlockIndex.of(pageId, body.blocks(), history);

        if (!reflection.hasAppliedOperation()) {
            requireUnchanged(reflection, index);
        }
        Map<String, NotionHistoryRowDto> blockIdToRow = requireHistoryRows(reflection, history);
        NotionWriteScopeDto scope = scopeOf(index, blockIdToRow, history.reflectedColumnIndex());

        Map<String, String> targetIdToLastCreated = new HashMap<>();
        reflection.operations().stream()
                .filter(operation -> operation.applied() && operation.isInsert() && operation.createdBlockId() != null)
                .forEach(operation -> targetIdToLastCreated.put(operation.targetBlockId(), operation.createdBlockId()));

        List<ReflectionOperationDto> operations = reflection.operations();
        for (int position = 0; position < operations.size(); position++) {
            ReflectionOperationDto operation = operations.get(position);
            if (operation.applied()) {
                continue;
            }
            String created = null;
            switch (operation.type()) {
                case UPDATE_TEXT -> notionClient.updateBlockText(operation.targetBlockId(), operation.blockType(), operation.text(), scope);
                case UPDATE_ROW -> notionClient.updateTableRow(operation.targetBlockId(), operation.cells(), scope);
                case INSERT_BLOCK_AFTER -> {
                    String after = targetIdToLastCreated.getOrDefault(operation.targetBlockId(), operation.targetBlockId());
                    created = notionClient.appendTextBlock(operation.parentBlockId(), after, operation.blockType(), operation.text(), scope);
                    scope = scope.withCreatedBlock(created, operation.blockType(), null);
                    targetIdToLastCreated.put(operation.targetBlockId(), created);
                }
                case INSERT_ROW_AFTER -> {
                    String after = targetIdToLastCreated.getOrDefault(operation.targetBlockId(), operation.targetBlockId());
                    created = notionClient.appendTableRow(operation.parentBlockId(), after, operation.cells(), scope);
                    scope = scope.withCreatedBlock(created, TABLE_ROW, operation.tableWidth());
                    targetIdToLastCreated.put(operation.targetBlockId(), created);
                }
                case DELETE -> notionClient.deleteBlock(operation.targetBlockId(), scope);
            }
            onApplied.accept(position, created);
        }

        String today = LocalDate.now(clock.withZone(TimeZoneConst.KST)).toString();
        for (NotionHistoryRowDto row : blockIdToRow.values()) {
            if (row.isReflected()) {
                continue;
            }
            List<String> cells = new ArrayList<>(row.cells());
            cells.set(history.reflectedColumnIndex(), today);
            notionClient.updateTableRow(row.blockId(), cells, scope);
        }
    }

    private void requireUnchanged(
            SpecReflectionDto reflection,
            SpecBlockIndex index
    ) {
        for (ReflectionOperationDto operation : reflection.operations()) {
            SpecBlockEntryDto entry = index.findByBlockId(operation.targetBlockId())
                    .orElseThrow(() -> new ReflectionStaleException(SPEC_CHANGED + "대상 블록 [" + operation.targetKey() + "]이 없습니다"));
            String base = reflection.baseBlocks().get(entry.blockId());
            if (base != null && !base.equals(entry.lastEditedTime())) {
                throw new ReflectionStaleException(SPEC_CHANGED + "대상 블록 [" + operation.targetKey() + "]이 수정되었습니다");
            }
        }
    }

    private Map<String, NotionHistoryRowDto> requireHistoryRows(
            SpecReflectionDto reflection,
            NotionHistoryTableDto history
    ) {
        Map<String, NotionHistoryRowDto> currentRows = new HashMap<>();
        history.rows().forEach(row -> currentRows.put(row.blockId(), row));

        Map<String, NotionHistoryRowDto> blockIdToRow = new LinkedHashMap<>();
        for (ReflectionHistoryRowDto stored : reflection.historyRows()) {
            NotionHistoryRowDto current = currentRows.get(stored.blockId());
            if (current == null) {
                throw new ReflectionStaleException(SPEC_CHANGED + "수정 이력 행 '" + stored.change() + "'이 없습니다");
            }
            if (!Objects.equals(current.sprint(), stored.sprint()) || !Objects.equals(current.change(), stored.change())) {
                throw new ReflectionStaleException(SPEC_CHANGED + "수정 이력 행 '" + stored.change() + "'의 내용이 바뀌었습니다");
            }
            blockIdToRow.put(current.blockId(), current);
        }
        return blockIdToRow;
    }

    private NotionWriteScopeDto scopeOf(
            SpecBlockIndex index,
            Map<String, NotionHistoryRowDto> blockIdToRow,
            int reflectedColumnIndex
    ) {
        Map<String, String> blockIdToType = new HashMap<>();
        Map<String, Integer> rowIdToWidth = new HashMap<>();
        Map<String, Integer> tableIdToWidth = new HashMap<>();
        Set<String> containerIds = new HashSet<>(Set.of(index.pageId()));
        Set<String> anchorIds = new HashSet<>();

        for (SpecBlockEntryDto entry : index.entries()) {
            anchorIds.add(entry.blockId());
            if (entry.isTable()) {
                if (!entry.basicTable()) {
                    tableIdToWidth.put(entry.blockId(), entry.tableWidth());
                }
            } else if (entry.isRow()) {
                if (entry.editable()) {
                    blockIdToType.put(entry.blockId(), TABLE_ROW);
                    rowIdToWidth.put(entry.blockId(), entry.tableWidth());
                }
            } else if (entry.editable() && NotionWriteGuard.TEXT_BLOCK_TYPES.contains(entry.type())) {
                blockIdToType.put(entry.blockId(), entry.type());
                containerIds.add(entry.blockId());
            }
        }

        Map<String, List<String>> historyRowIdToCells = new HashMap<>();
        blockIdToRow.values().forEach(row -> historyRowIdToCells.put(row.blockId(), row.cells()));
        return NotionWriteScopeDto.of(index.pageId(), blockIdToType, rowIdToWidth, tableIdToWidth, containerIds, anchorIds, historyRowIdToCells, reflectedColumnIndex);
    }
}
