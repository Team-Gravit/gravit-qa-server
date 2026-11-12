package gravit.qa.adapter.notion.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.exception.InvalidNotionSpecException;
import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;
import gravit.qa.adapter.notion.dto.internal.NotionHistoryRowDto;
import gravit.qa.adapter.notion.dto.internal.NotionHistoryTableDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.글자;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.문단;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.제목3;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.표_블록;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.표_행;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class NotionHistoryTableParserTest {

    private static final String HEADING_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaa0001";
    private static final String TABLE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaa0002";
    private static final String ROW_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaa0004";

    @Nested
    @DisplayName("수정 이력 표를 읽을 때")
    class 이력_표 {

        @Test
        void H3_수정_이력_바로_다음_표의_머리글로_열을_찾아_행을_읽는다() {
            // given
            List<NotionBlockDto> blocks = 본문(
                    표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0003", "반영일", "스프린트", "수정 사항"),
                    표_행(ROW_ID, "", "4th Agile", "로그인 버튼에 Apple 추가"),
                    표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0005", "2026-10-01", "3rd Agile", "문구 수정")
            );

            // when
            NotionHistoryTableDto history = NotionHistoryTableParser.parse(blocks);

            // then
            assertSoftly(softly -> {
                softly.assertThat(history.headingBlockId()).isEqualTo(HEADING_ID);
                softly.assertThat(history.tableBlockId()).isEqualTo(TABLE_ID);
                softly.assertThat(history.reflectedColumnIndex()).isZero();
                softly.assertThat(history.rows()).extracting(NotionHistoryRowDto::sprint).containsExactly("4th Agile", "3rd Agile");
                softly.assertThat(history.rows().getFirst().blockId()).isEqualTo(ROW_ID);
                softly.assertThat(history.rows().getFirst().change()).isEqualTo("로그인 버튼에 Apple 추가");
                softly.assertThat(history.rows().getFirst().cells()).containsExactly("", "4th Agile", "로그인 버튼에 Apple 추가");
            });
        }

        @Test
        void 수정_이력_제목이_없거나_뒤에_표가_없으면_빈_이력이다() {
            // given
            List<NotionBlockDto> noHeading = List.of(new NotionBlockDto(문단("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0009", 글자("본문")), List.of()));
            List<NotionBlockDto> noTable = List.of(new NotionBlockDto(제목3(HEADING_ID, "수정 이력"), List.of()));

            // when & then
            assertThat(NotionHistoryTableParser.parse(noHeading).exists()).isFalse();
            assertThat(NotionHistoryTableParser.parse(noTable).rows()).isEmpty();
        }

        @Test
        void 빈_행은_건너뛴다() {
            // given
            List<NotionBlockDto> blocks = 본문(
                    표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0003", "스프린트", "수정 사항", "반영일"),
                    표_행(ROW_ID, "", "", "")
            );

            // when & then
            assertThat(NotionHistoryTableParser.parse(blocks).rows()).isEmpty();
        }

        @Test
        void 머리글에_반영일_열이_없으면_읽지_못한_명세로_본다() {
            // given
            List<NotionBlockDto> blocks = 본문(
                    표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0003", "스프린트", "수정 사항"),
                    표_행(ROW_ID, "4th Agile", "변경")
            );

            // when & then
            assertThatThrownBy(() -> NotionHistoryTableParser.parse(blocks))
                    .isInstanceOf(InvalidNotionSpecException.class)
                    .hasMessage("수정 이력 표 머리글에 '반영일' 열이 없습니다");
        }
    }

    @Nested
    @DisplayName("반영일로 반영 여부를 판정할 때")
    class 반영일 {

        @Test
        void 반영일이_비었거나_하이픈이면_반영하지_않은_행이고_날짜가_있으면_반영한_행이다() {
            // given
            List<NotionBlockDto> blocks = 본문(
                    표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0003", "스프린트", "수정 사항", "반영일"),
                    표_행(ROW_ID, "4th Agile", "비어 있음", ""),
                    표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0006", "4th Agile", "하이픈", "-"),
                    표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0007", "4th Agile", "반영됨", "2026-11-12")
            );

            // when
            List<NotionHistoryRowDto> rows = NotionHistoryTableParser.parse(blocks).rows();

            // then
            assertThat(rows).extracting(NotionHistoryRowDto::isReflected).containsExactly(false, false, true);
        }

        @Test
        void 스프린트_값은_앞뒤_공백만_무시하고_이름과_정확히_같아야_이번_스프린트_행이다() {
            // given
            List<NotionBlockDto> blocks = 본문(
                    표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0003", "스프린트", "수정 사항", "반영일"),
                    표_행(ROW_ID, " 4th Agile ", "같음", ""),
                    표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0006", "4th agile", "대소문자 다름", "")
            );

            // when
            List<NotionHistoryRowDto> rows = NotionHistoryTableParser.parse(blocks).rows();

            // then
            assertThat(rows).extracting(row -> row.belongsTo("4th Agile")).containsExactly(true, false);
        }
    }

    private List<NotionBlockDto> 본문(ObjectNode... rows) {
        List<NotionBlockDto> tableRows = Arrays.stream(rows).map(row -> new NotionBlockDto(row, List.of())).toList();
        return List.of(
                new NotionBlockDto(문단("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0008", 글자("본문")), List.of()),
                new NotionBlockDto(제목3(HEADING_ID, "수정 이력"), List.of()),
                new NotionBlockDto(표_블록(TABLE_ID, 3), tableRows)
        );
    }
}
