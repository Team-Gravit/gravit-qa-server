package gravit.qa.checklist.domain;

import gravit.qa.checklist.dto.internal.CodeEvidenceDto;
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
    private static final String REASON = "항목 수정으로 판정 초기화";
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
            Optional<CheckCellHistory> history = web.applyParts(List.of(Part.SERVER), REASON, ADMIN_ID, NOW);

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
            Optional<CheckCellHistory> history = app.applyParts(List.of(Part.WEB, Part.APP), REASON, ADMIN_ID, NOW);

            // then
            assertThat(app.getState()).isEqualTo(CellState.UNCHECKED);
            assertThat(history).map(CheckCellHistory::getFromState).contains(CellState.NOT_APPLICABLE);
        }

        @Test
        void 포함_여부가_그대로면_이력을_남기지_않는다() {
            // given
            CheckCell web = 셀(Part.WEB, List.of(Part.WEB));

            // when
            Optional<CheckCellHistory> history = web.applyParts(List.of(Part.WEB, Part.SERVER), REASON, ADMIN_ID, NOW);

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
            CheckCellHistory history = web.check(CellState.FAIL, null, ADMIN_ID, NOW).orElseThrow();

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

        @Test
        void FAIL로_바꿀_때만_사유를_셀과_이력에_남긴다() {
            // given
            CheckCell web = 셀(Part.WEB, List.of(Part.WEB));
            CheckCell app = 셀(Part.APP, List.of(Part.APP));

            // when
            CheckCellHistory fail = web.check(CellState.FAIL, "버튼 문구가 다르다", ADMIN_ID, NOW).orElseThrow();
            CheckCellHistory pass = app.check(CellState.PASS, "버튼 문구가 다르다", ADMIN_ID, NOW).orElseThrow();

            // then
            assertSoftly(softly -> {
                softly.assertThat(web.getReason()).isEqualTo("버튼 문구가 다르다");
                softly.assertThat(fail.getReason()).isEqualTo("버튼 문구가 다르다");
                softly.assertThat(app.getReason()).isNull();
                softly.assertThat(pass.getReason()).isNull();
            });
        }

        @Test
        void 이미_FAIL인_셀에_다른_사유를_보내면_사유만_바꾸고_근거는_그대로_둔다() {
            // given
            CodeEvidenceDto evidence = CodeEvidenceDto.of("gravit-web/src/pages/notice.tsx", 10, 20);
            CheckCell web = 셀(Part.WEB, List.of(Part.WEB));
            web.judge(CellState.FAIL, "1차 사유", List.of(evidence), NOW);

            // when
            CheckCellHistory history = web.check(CellState.FAIL, "사람 사유", ADMIN_ID, NOW).orElseThrow();

            // then
            assertSoftly(softly -> {
                softly.assertThat(web.getState()).isEqualTo(CellState.FAIL);
                softly.assertThat(web.getSource()).isEqualTo(CellSource.MANUAL);
                softly.assertThat(web.getReason()).isEqualTo("사람 사유");
                softly.assertThat(web.getEvidence()).containsExactly(evidence);
                softly.assertThat(history.getFromState()).isEqualTo(CellState.FAIL);
                softly.assertThat(history.getToState()).isEqualTo(CellState.FAIL);
                softly.assertThat(history.getReason()).isEqualTo("사람 사유");
                softly.assertThat(history.getEvidence()).containsExactly(evidence);
                softly.assertThat(history.getChangedBy()).isEqualTo(ADMIN_ID);
            });
        }

        @Test
        void 상태가_같고_사유가_없거나_같으면_바꾸지_않는다() {
            // given
            CheckCell web = 셀(Part.WEB, List.of(Part.WEB));
            web.judge(CellState.FAIL, "1차 사유", List.of(), NOW);
            CheckCell app = 셀(Part.APP, List.of(Part.APP));
            app.check(CellState.PASS, null, ADMIN_ID, NOW);

            // when & then
            assertSoftly(softly -> {
                softly.assertThat(web.check(CellState.FAIL, null, ADMIN_ID, NOW)).isEmpty();
                softly.assertThat(web.check(CellState.FAIL, "1차 사유", ADMIN_ID, NOW)).isEmpty();
                softly.assertThat(app.check(CellState.PASS, "무시되는 사유", ADMIN_ID, NOW)).isEmpty();
                softly.assertThat(web.getSource()).isEqualTo(CellSource.AUTO);
                softly.assertThat(web.getReason()).isEqualTo("1차 사유");
            });
        }
    }

    @Nested
    @DisplayName("1차 판정할 때")
    class 판정 {

        private static final CodeEvidenceDto EVIDENCE = CodeEvidenceDto.of("gravit-server/src/main/java/gravit/code/notice/service/NoticeQueryService.java", 46, 50);

        @Test
        void 사유와_근거를_셀과_이력에_따로_남긴다() {
            // given
            CheckCell server = 셀(Part.SERVER, List.of(Part.SERVER));

            // when
            CheckCellHistory history = server.judge(CellState.PASS, "없는 공지는 NOTICE_NOT_FOUND를 던진다", List.of(EVIDENCE), NOW);

            // then
            assertSoftly(softly -> {
                softly.assertThat(server.getState()).isEqualTo(CellState.PASS);
                softly.assertThat(server.getSource()).isEqualTo(CellSource.AUTO);
                softly.assertThat(server.getReason()).isEqualTo("없는 공지는 NOTICE_NOT_FOUND를 던진다");
                softly.assertThat(server.getEvidence()).containsExactly(EVIDENCE);
                softly.assertThat(history.getReason()).isEqualTo("없는 공지는 NOTICE_NOT_FOUND를 던진다");
                softly.assertThat(history.getEvidence()).containsExactly(EVIDENCE);
                softly.assertThat(history.getChangedBy()).isNull();
            });
        }

        @Test
        void 사람이_바꾸면_근거를_비운다() {
            // given
            CheckCell server = 셀(Part.SERVER, List.of(Part.SERVER));
            server.judge(CellState.FAIL, "이전, 다음 필드가 없다", List.of(EVIDENCE), NOW);

            // when
            CheckCellHistory history = server.check(CellState.PASS, null, ADMIN_ID, NOW).orElseThrow();

            // then
            assertThat(server.getEvidence()).isEmpty();
            assertThat(server.getReason()).isNull();
            assertThat(history.getEvidence()).isEmpty();
        }

        @Test
        void 항목_수정으로_초기화하면_근거를_비우고_초기화_사유를_남긴다() {
            // given
            CheckCell server = 셀(Part.SERVER, List.of(Part.SERVER));
            server.judge(CellState.PASS, "코드에서 확인", List.of(EVIDENCE), NOW);

            // when
            Optional<CheckCellHistory> history = server.resetJudgment(REASON, ADMIN_ID, NOW);

            // then
            assertThat(server.getState()).isEqualTo(CellState.UNCHECKED);
            assertThat(server.getReason()).isEqualTo(REASON);
            assertThat(server.getEvidence()).isEmpty();
            assertThat(history).hasValueSatisfying(value -> assertThat(value.getEvidence()).isEmpty());
        }

        @Test
        void 이미_사람이_둔_UNCHECKED여도_초기화_기록을_남긴다() {
            // given
            CheckCell server = 셀(Part.SERVER, List.of(Part.SERVER));
            server.judge(CellState.FAIL, "1차 사유", List.of(EVIDENCE), NOW);
            server.check(CellState.UNCHECKED, null, ADMIN_ID, NOW);

            // when
            Optional<CheckCellHistory> history = server.resetJudgment(REASON, ADMIN_ID, NOW);

            // then
            assertThat(history).hasValueSatisfying(value -> assertSoftly(softly -> {
                softly.assertThat(value.getFromState()).isEqualTo(CellState.UNCHECKED);
                softly.assertThat(value.getToState()).isEqualTo(CellState.UNCHECKED);
                softly.assertThat(value.getSource()).isEqualTo(CellSource.MANUAL);
                softly.assertThat(value.getReason()).isEqualTo(REASON);
            }));
        }

        @Test
        void 해당_없는_셀은_초기화_기록을_남기지_않는다() {
            // given
            CheckCell app = 셀(Part.APP, List.of(Part.SERVER));

            // when
            Optional<CheckCellHistory> history = app.resetJudgment(REASON, ADMIN_ID, NOW);

            // then
            assertThat(history).isEmpty();
            assertThat(app.getState()).isEqualTo(CellState.NOT_APPLICABLE);
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
