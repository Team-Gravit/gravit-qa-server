package gravit.qa.reflection.dto.internal;

import java.util.List;

public record SpecBlockEntryDto(
        String key,
        String blockId,
        String type,
        String parentId,
        int depth,
        boolean editable,
        boolean headerRow,
        boolean basicTable,
        Integer tableWidth,
        String text,
        List<String> cells,
        String lastEditedTime
) {

    public boolean isRow() {
        return cells != null;
    }

    public boolean isTable() {
        return "table".equals(type);
    }
}
