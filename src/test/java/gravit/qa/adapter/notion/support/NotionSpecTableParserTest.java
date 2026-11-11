package gravit.qa.adapter.notion.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.exception.InvalidNotionSpecException;
import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecTableDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.AI_01_기본_정보_표;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.AI_01_본문;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.ERR_01_기본_정보_표;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.ERR_01_본문;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.MY_04_기본_정보_표;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.MY_04_본문;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.QUIZ_02_기본_정보_표;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.QUIZ_02_본문;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.글자;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.문단;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.본문_응답;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.블록_트리;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.코드;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.표_블록;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.행;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.행_응답;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.헤더_행;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class NotionSpecTableParserTest {

    private static final String TABLE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PARAGRAPH_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Nested
    @DisplayName("표본 명세의 기본 정보 표를 읽을 때")
    class 표본 {

        @Test
        void QUIZ_02는_관련_API_2개와_인라인_코드_라우트와_Figma_링크를_읽는다() {
            // when
            NotionSpecTableDto table = NotionSpecTableParser.parse(블록_트리(QUIZ_02_본문(), QUIZ_02_기본_정보_표()));

            // then
            assertSoftly(softly -> {
                softly.assertThat(table.specStatus()).isEqualTo("확정");
                softly.assertThat(table.parts()).containsExactly("서버", "웹", "앱");
                softly.assertThat(table.route()).isEqualTo("/learning/lessons/$lessonId");
                softly.assertThat(table.relatedApis()).containsExactly(
                        "GET /api/v1/problems/{lessonId}",
                        "POST /api/v1/problems/results"
                );
                softly.assertThat(table.figmaUrl()).isEqualTo("https://www.figma.com/design/hu4c6qCEMB62qHXk2v8Gsl/?node-id=15432-32878");
                softly.assertThat(table.feSpecIds()).isEmpty();
            });
        }

        @Test
        void MY_04는_관련_API_11개를_셀_순서대로_읽는다() {
            // when
            NotionSpecTableDto table = NotionSpecTableParser.parse(블록_트리(MY_04_본문(), MY_04_기본_정보_표()));

            // then
            assertThat(table.relatedApis()).containsExactly(
                    "GET /api/v1/friends/count",
                    "GET /api/v1/friends/follower",
                    "GET /api/v1/friends/following",
                    "POST /api/v1/social/follow/{userId}",
                    "POST /api/v1/friends/unfollowing/{followeeId}",
                    "GET /api/v1/social/feed",
                    "POST /api/v1/social/feed/{feedId}/congratulate",
                    "GET /api/v1/social/recommend",
                    "POST /api/v1/friends/following/{followeeId}",
                    "POST /api/v1/friends/reject-following/{followerId}",
                    "DELETE /api/v1/social/feed/{feedId}"
            );
        }

        @Test
        void ERR_01은_라우트와_관련_API의_하이픈을_빈_값으로_읽는다() {
            // when
            NotionSpecTableDto table = NotionSpecTableParser.parse(블록_트리(ERR_01_본문(), ERR_01_기본_정보_표()));

            // then
            assertSoftly(softly -> {
                softly.assertThat(table.parts()).containsExactly("웹", "앱");
                softly.assertThat(table.route()).isNull();
                softly.assertThat(table.relatedApis()).isEmpty();
                softly.assertThat(table.feSpecIds()).containsExactly("E.1");
            });
        }

        @Test
        void AI_01은_서식_없는_표와_예전_형식_본문에서도_확인_필요를_읽는다() {
            // when
            NotionSpecTableDto table = NotionSpecTableParser.parse(블록_트리(AI_01_본문(), AI_01_기본_정보_표()));

            // then
            assertSoftly(softly -> {
                softly.assertThat(table.specStatus()).isEqualTo("확인 필요");
                softly.assertThat(table.parts()).containsExactly("웹", "앱");
                softly.assertThat(table.route()).isNull();
                softly.assertThat(table.relatedApis()).isEmpty();
                softly.assertThat(table.figmaUrl()).isEqualTo("https://www.figma.com/design/hu4c6qCEMB62qHXk2v8Gsl/?node-id=15432-38774");
            });
        }
    }

    @Nested
    @DisplayName("셀 값을 나눌 때")
    class 셀_값 {

        @Test
        void 관련_API는_조각_경계와_관계없이_줄로_나누고_목록_기호와_중복을_지운다() {
            // given
            List<NotionBlockDto> blocks = 표만(
                    행("명세 상태", 글자("확정")),
                    행("관련 API", 글자("- GET   /api/v1/a\n"), 코드("POST /api/v1/b"), 글자("\n\n• GET /api/v1/a"))
            );

            // when
            NotionSpecTableDto table = NotionSpecTableParser.parse(blocks);

            // then
            assertThat(table.relatedApis()).containsExactly("GET /api/v1/a", "POST /api/v1/b");
        }

        @Test
        void 여러_라우트는_문자열_하나로_두고_FE_스펙_ID는_쉼표로_나눈다() {
            // given
            List<NotionBlockDto> blocks = 표만(
                    행("명세 상태", 글자("확정")),
                    행("라우트", 코드("/my"), 글자(", "), 코드("/my/summary")),
                    행("FE 스펙 ID", 글자("O.2, O.3,O.4"))
            );

            // when
            NotionSpecTableDto table = NotionSpecTableParser.parse(blocks);

            // then
            assertSoftly(softly -> {
                softly.assertThat(table.route()).isEqualTo("/my, /my/summary");
                softly.assertThat(table.feSpecIds()).containsExactly("O.2", "O.3", "O.4");
            });
        }

        @Test
        void 없는_항목은_빈_값이고_모르는_항목은_무시한다() {
            // given
            List<NotionBlockDto> blocks = 표만(
                    행("명세 상태", 글자("확정")),
                    행("담당자", 글자("서버"))
            );

            // when
            NotionSpecTableDto table = NotionSpecTableParser.parse(blocks);

            // then
            assertSoftly(softly -> {
                softly.assertThat(table.parts()).isEmpty();
                softly.assertThat(table.route()).isNull();
                softly.assertThat(table.relatedApis()).isEmpty();
                softly.assertThat(table.figmaUrl()).isNull();
                softly.assertThat(table.feSpecIds()).isEmpty();
            });
        }
    }

    @Nested
    @DisplayName("기본 정보 표를 읽을 수 없으면")
    class 읽기_실패 {

        @Test
        void 첫_블록이_표가_아니면_사유를_담아_실패한다() {
            // given
            List<NotionBlockDto> blocks = 블록_트리(
                    본문_응답(문단(PARAGRAPH_ID, 글자("목적")), 표_블록(TABLE_ID)),
                    행_응답(헤더_행(), 행("명세 상태", 글자("확정")))
            );

            // when & then
            assertThatThrownBy(() -> NotionSpecTableParser.parse(blocks))
                    .isInstanceOf(InvalidNotionSpecException.class)
                    .hasMessage("본문 첫 블록이 기본 정보 표가 아닙니다");
        }

        @Test
        void 본문이_비어_있으면_실패한다() {
            // when & then
            assertThatThrownBy(() -> NotionSpecTableParser.parse(List.of()))
                    .isInstanceOf(InvalidNotionSpecException.class)
                    .hasMessage("본문 첫 블록이 기본 정보 표가 아닙니다");
        }

        @Test
        void 명세_상태_행이_없으면_실패한다() {
            // given
            List<NotionBlockDto> blocks = 표만(행("파트", 글자("웹")));

            // when & then
            assertThatThrownBy(() -> NotionSpecTableParser.parse(blocks))
                    .isInstanceOf(InvalidNotionSpecException.class)
                    .hasMessage("기본 정보 표에 '명세 상태' 값이 없습니다");
        }

        @Test
        void 명세_상태가_하이픈이면_실패한다() {
            // given
            List<NotionBlockDto> blocks = 표만(행("명세 상태", 글자(" - ")));

            // when & then
            assertThatThrownBy(() -> NotionSpecTableParser.parse(blocks))
                    .isInstanceOf(InvalidNotionSpecException.class)
                    .hasMessage("기본 정보 표에 '명세 상태' 값이 없습니다");
        }
    }

    private static List<NotionBlockDto> 표만(ObjectNode... rows) {
        ObjectNode[] withHeader = new ObjectNode[rows.length + 1];
        withHeader[0] = 헤더_행();
        System.arraycopy(rows, 0, withHeader, 1, rows.length);
        return 블록_트리(본문_응답(표_블록(TABLE_ID)), 행_응답(withHeader));
    }
}
