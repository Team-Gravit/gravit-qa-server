package gravit.qa.checklist.dto.internal;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.feature.domain.Part;

public record CellStateCountDto(
        long sprintId,
        Part part,
        CellState state,
        long count
) {
}
