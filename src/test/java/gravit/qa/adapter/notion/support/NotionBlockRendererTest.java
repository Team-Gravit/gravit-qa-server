package gravit.qa.adapter.notion.support;

import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.QUIZ_02_기본_정보_표;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.QUIZ_02_본문;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.블록_트리;
import static org.assertj.core.api.Assertions.assertThat;

class NotionBlockRendererTest {

    @Nested
    @DisplayName("표를 마크다운으로 바꿀 때")
    class 표 {

        @Test
        void 셀_안의_줄바꿈은_br로_바꿔_행을_한_줄로_유지한다() {
            // given
            List<NotionBlockDto> blocks = 블록_트리(QUIZ_02_본문(), QUIZ_02_기본_정보_표());

            // when
            String markdown = NotionBlockRenderer.toMarkdown(blocks);

            // then
            assertThat(markdown.lines()).contains(
                    "| 항목 | 값 |",
                    "|---|---|",
                    "| 관련 API |   • GET /api/v1/problems/{lessonId}<br>  • POST /api/v1/problems/results |"
            );
        }

        @Test
        void 첫_표_뒤의_H3_섹션_제목은_마크다운_H3로_남긴다() {
            // given
            List<NotionBlockDto> blocks = 블록_트리(QUIZ_02_본문(), QUIZ_02_기본_정보_표());

            // when
            String markdown = NotionBlockRenderer.toMarkdown(blocks);

            // then
            assertThat(markdown.lines()).contains("### 화면에 보이는 것", "### 사용자가 할 수 있는 것", "### 예외 상황");
        }
    }
}
