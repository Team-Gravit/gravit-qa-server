package gravit.qa.adapter.notion.dto.internal;

import java.util.List;

public record NotionHistoryRowDto(
        String blockId,
        String sprint,
        String change,
        String reflectedOn,
        List<String> cells
) {

    public boolean isReflected() {
        return reflectedOn != null;
    }

    public boolean belongsTo(String sprintKey) {
        return sprintKey != null && sprintKey.trim().equals(sprint);
    }
}
