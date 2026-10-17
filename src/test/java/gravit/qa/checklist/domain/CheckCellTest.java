package gravit.qa.checklist.domain;

import gravit.qa.feature.domain.Part;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class CheckCellTest {

    private static final long ITEM_ID = 1L;
    private static final long ADMIN_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 12, 0);

    @Nested
    @DisplayName("셀을 만들 때")
    class 생성 {

        @Test
        void 파트_3개를_모두_만들고_항목_파트는_UNCHECKED_나머지는_NOT_APPLICABLE이다() {
            // when
            List<CheckCell> cells = CheckCell.initialize(ITEM_ID, List.of(Part.WEB));

            // then
            assertThat(cells)
                    .extracting(CheckCell::getPart, CheckCell::getState, CheckCell::getSource)
                    .containsExactly(
                            tuple(Part.SERVER, CellState.NOT_APPLICABLE, CellSource.MANUAL),
                            tuple(Part.WEB, CellState.UNCHECKED, CellSource.MANUAL),
                            tuple(Part.APP, CellState.NOT_APPLICABLE, CellSource.MANUAL)
                    );
        }
    }

    @Nested
    @DisplayName("항목 파트가 바뀔 때")
    class 파트_변경 {

        @Test
        void 빠진_파트의_셀은_NOT_APPLICABLE이_되고_이력을_남긴다() {
            // given
            CheckCell web = 셀(Part.WEB, List.of(Part.WEB, Part.SERVER));

            // when
            Optional<CheckCellHistory> history = web.applyParts(List.of(Part.SERVER), ADMIN_ID, NOW);

            // then
            assertSoftly(softly -> {
                softly.assertThat(web.getState()).isEqualTo(CellState.NOT_APPLICABLE);
                softly.assertThat(web.getUpdatedBy()).isEqualTo(ADMIN_ID);
                softly.assertThat(history).hasValueSatisfying(value -> {
                    softly.assertThat(value.getFromState()).isEqualTo(CellState.UNCHECKED);
                    softly.assertThat(value.getToState()).isEqualTo(CellState.NOT_APPLICABLE);
                    softly.assertThat(value.getChangedBy()).isEqualTo(ADMIN_ID);
                });
            });
        }

        @Test
        void 다시_들어온_파트의_셀은_UNCHECKED가_된다() {
            // given
            CheckCell app = 셀(Part.APP, List.of(Part.WEB));

            // when
            Optional<CheckCellHistory> history = app.applyParts(List.of(Part.WEB, Part.APP), ADMIN_ID, NOW);

            // then
            assertThat(app.getState()).isEqualTo(CellState.UNCHECKED);
            assertThat(history).map(CheckCellHistory::getFromState).contains(CellState.NOT_APPLICABLE);
        }

        @Test
        void 포함_여부가_그대로면_이력을_남기지_않는다() {
            // given
            CheckCell web = 셀(Part.WEB, List.of(Part.WEB));

            // when
            Optional<CheckCellHistory> history = web.applyParts(List.of(Part.WEB, Part.SERVER), ADMIN_ID, NOW);

            // then
            assertThat(history).isEmpty();
            assertThat(web.getState()).isEqualTo(CellState.UNCHECKED);
        }
    }

    @Nested
    @DisplayName("수동 체크할 때")
    class 체크 {

        @Test
        void 상태를_바꾸고_이전_새_상태와_변경자를_이력으로_돌려준다() {
            // given
            CheckCell web = 셀(Part.WEB, List.of(Part.WEB));

            // when
            CheckCellHistory history = web.check(CellState.FAIL, ADMIN_ID, NOW);

            // then
            assertSoftly(softly -> {
                softly.assertThat(web.getState()).isEqualTo(CellState.FAIL);
                softly.assertThat(web.getSource()).isEqualTo(CellSource.MANUAL);
                softly.assertThat(web.getUpdatedAt()).isEqualTo(NOW);
                softly.assertThat(history.getFromState()).isEqualTo(CellState.UNCHECKED);
                softly.assertThat(history.getToState()).isEqualTo(CellState.FAIL);
                softly.assertThat(history.getSource()).isEqualTo(CellSource.MANUAL);
                softly.assertThat(history.getChangedAt()).isEqualTo(NOW);
            });
        }
    }

    private static CheckCell 셀(
            Part part,
            List<Part> itemParts
    ) {
        return CheckCell.initialize(ITEM_ID, itemParts).stream()
                .filter(cell -> cell.getPart() == part)
                .findFirst()
                .orElseThrow();
    }
}
