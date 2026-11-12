package gravit.qa.reflection.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gravit.qa.reflection.domain.ReflectionOperationType;

import java.util.List;

public record ReflectionOperationDto(
        int historyRow,
        ReflectionOperationType type,
        String targetKey,
        String targetBlockId,
        String parentBlockId,
        String blockType,
        String text,
        List<String> cells,
        Integer tableWidth,
        boolean applied,
        String createdBlockId
) {

    public static ReflectionOperationDto of(
            int historyRow,
            ReflectionOperationType type,
            String targetKey,
            String targetBlockId,
            String parentBlockId,
            String blockType,
            String text,
            List<String> cells,
            Integer tableWidth
    ) {
        return new ReflectionOperationDto(
                historyRow,
                type,
                targetKey,
                targetBlockId,
                parentBlockId,
                blockType,
                text,
                cells == null ? null : List.copyOf(cells),
                tableWidth,
                false,
                null
        );
    }

    public ReflectionOperationDto markApplied(String createdBlockId) {
        return new ReflectionOperationDto(historyRow, type, targetKey, targetBlockId, parentBlockId, blockType, text, cells, tableWidth, true, createdBlockId);
    }

    @JsonIgnore
    public boolean isInsert() {
        return type == ReflectionOperationType.INSERT_BLOCK_AFTER || type == ReflectionOperationType.INSERT_ROW_AFTER;
    }
}
