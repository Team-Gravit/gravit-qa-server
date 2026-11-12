package gravit.qa.checklist.support;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.dto.internal.StateCountDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProgressCalculator {

    private static final int FULL_RATE = 100;

    public static StateCountDto count(Map<CellState, Long> stateToCount) {
        long pass = stateToCount.getOrDefault(CellState.PASS, 0L);
        long fail = stateToCount.getOrDefault(CellState.FAIL, 0L);
        long unchecked = stateToCount.getOrDefault(CellState.UNCHECKED, 0L);
        return StateCountDto.of(pass + fail + unchecked, pass + fail, pass, fail);
    }

    public static int completionRate(Collection<CellState> states) {
        long total = states.stream().filter(state -> state != CellState.NOT_APPLICABLE).count();
        if (total == 0) {
            return FULL_RATE;
        }
        long checked = states.stream().filter(state -> state == CellState.PASS || state == CellState.FAIL).count();
        return (int) (checked * FULL_RATE / total);
    }
}
