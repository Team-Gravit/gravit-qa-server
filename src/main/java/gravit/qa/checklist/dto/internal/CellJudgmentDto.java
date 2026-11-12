package gravit.qa.checklist.dto.internal;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.feature.domain.Part;

public record CellJudgmentDto(
        long itemId,
        Part part,
        CellState state,
        String reason
) {

    public static CellJudgmentDto of(
            long itemId,
            Part part,
            CellState state,
            String reason
    ) {
        return new CellJudgmentDto(itemId, part, state, reason);
    }
}
