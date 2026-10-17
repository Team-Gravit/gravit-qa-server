package gravit.qa.checklist.support;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.domain.ReviewState;
import gravit.qa.feature.domain.Part;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.sprint.domain.SprintStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

import static gravit.qa.global.exception.domain.CustomErrorCode.CHECK_CELL_LOCKED;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CheckCellEditPolicy {

    private static final Set<Part> AUTO_JUDGED_PARTS = EnumSet.of(Part.WEB, Part.SERVER);

    public static boolean isEditable(
            SprintStatus sprintStatus,
            Classification classification,
            ReviewState reviewState,
            Part part,
            CellState state
    ) {
        return sprintStatus == SprintStatus.IN_PROGRESS && isManuallyCheckable(classification, reviewState, part, state);
    }

    public static void requireEditable(
            Classification classification,
            ReviewState reviewState,
            Part part,
            CellState state
    ) {
        if (!isManuallyCheckable(classification, reviewState, part, state)) {
            throw new RestApiException(CHECK_CELL_LOCKED);
        }
    }

    private static boolean isManuallyCheckable(
            Classification classification,
            ReviewState reviewState,
            Part part,
            CellState state
    ) {
        return reviewState != ReviewState.REJECTED
                && state != CellState.NOT_APPLICABLE
                && !(classification == Classification.AUTO && AUTO_JUDGED_PARTS.contains(part));
    }
}
