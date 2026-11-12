package gravit.qa.checklist.support;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.dto.internal.StateCountDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressCalculatorTest {

    @Nested
    @DisplayName("파트별 진행도를 셀 때")
    class 진행도 {

        @Test
        void 해당_없는_셀은_빼고_UNCHECKED가_아닌_수를_checked로_센다() {
            // when
            StateCountDto count = ProgressCalculator.count(Map.of(
                    CellState.PASS, 3L,
                    CellState.FAIL, 1L,
                    CellState.UNCHECKED, 2L,
                    CellState.NOT_APPLICABLE, 5L
            ));

            // then
            assertThat(count).isEqualTo(StateCountDto.of(6, 4, 3, 1));
        }

        @Test
        void 셀이_없으면_모두_0이다() {
            // when & then
            assertThat(ProgressCalculator.count(Map.of())).isEqualTo(StateCountDto.of(0, 0, 0, 0));
        }
    }

    @Nested
    @DisplayName("화면 검수 비율을 낼 때")
    class 검수_비율 {

        @Test
        void 해당_없는_셀을_빼고_버림으로_계산한다() {
            // when & then
            assertThat(ProgressCalculator.completionRate(List.of(CellState.PASS, CellState.FAIL, CellState.UNCHECKED, CellState.NOT_APPLICABLE))).isEqualTo(66);
            assertThat(ProgressCalculator.completionRate(List.of(CellState.PASS, CellState.NOT_APPLICABLE))).isEqualTo(100);
            assertThat(ProgressCalculator.completionRate(List.of(CellState.UNCHECKED))).isZero();
        }

        @Test
        void 검수할_셀이_없으면_100이다() {
            // when & then
            assertThat(ProgressCalculator.completionRate(List.of(CellState.NOT_APPLICABLE))).isEqualTo(100);
            assertThat(ProgressCalculator.completionRate(List.of())).isEqualTo(100);
        }
    }
}
