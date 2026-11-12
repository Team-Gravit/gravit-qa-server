package gravit.qa.checklist.dto.internal;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.feature.domain.Part;

import java.util.List;

public record CellJudgmentDto(
        long itemId,
        Part part,
        CellState state,
        String reason,
        List<CodeEvidenceDto> evidence
) {

    public static CellJudgmentDto of(
            long itemId,
            Part part,
            CellState state,
            String reason,
            List<CodeEvidenceDto> evidence
    ) {
        return new CellJudgmentDto(itemId, part, state, reason, List.copyOf(evidence));
    }
}
