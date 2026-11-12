package gravit.qa.adapter.notion.dto.internal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record NotionWriteScopeDto(
        String pageId,
        Map<String, String> blockIdToType,
        Map<String, Integer> rowIdToWidth,
        Map<String, Integer> tableIdToWidth,
        Set<String> containerIds,
        Set<String> anchorIds,
        Map<String, List<String>> historyRowIdToCells,
        int reflectedColumnIndex
) {

    public static NotionWriteScopeDto of(
            String pageId,
            Map<String, String> blockIdToType,
            Map<String, Integer> rowIdToWidth,
            Map<String, Integer> tableIdToWidth,
            Set<String> containerIds,
            Set<String> anchorIds,
            Map<String, List<String>> historyRowIdToCells,
            int reflectedColumnIndex
    ) {
        return new NotionWriteScopeDto(
                pageId,
                Map.copyOf(blockIdToType),
                Map.copyOf(rowIdToWidth),
                Map.copyOf(tableIdToWidth),
                Set.copyOf(containerIds),
                Set.copyOf(anchorIds),
                Map.copyOf(historyRowIdToCells),
                reflectedColumnIndex
        );
    }

    public NotionWriteScopeDto withCreatedBlock(
            String blockId,
            String type,
            Integer rowWidth
    ) {
        Map<String, String> blocks = new HashMap<>(blockIdToType);
        blocks.put(blockId, type);
        Map<String, Integer> rows = new HashMap<>(rowIdToWidth);
        if (rowWidth != null) {
            rows.put(blockId, rowWidth);
        }
        Set<String> anchors = new HashSet<>(anchorIds);
        anchors.add(blockId);
        return of(pageId, blocks, rows, tableIdToWidth, containerIds, anchors, historyRowIdToCells, reflectedColumnIndex);
    }

    public boolean isKnownBlock(String blockId) {
        return anchorIds.contains(blockId) || blockIdToType.containsKey(blockId);
    }
}
