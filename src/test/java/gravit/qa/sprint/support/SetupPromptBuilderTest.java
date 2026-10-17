package gravit.qa.sprint.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.figma.domain.FigmaPlatform;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameDto;
import gravit.qa.adapter.figma.dto.internal.FigmaScreenDto;
import gravit.qa.adapter.github.dto.internal.GithubChangedFileDto;
import gravit.qa.adapter.llm.support.LlmPromptTemplates;
import gravit.qa.adapter.notion.dto.internal.NotionSpecDto;
import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.checklist.fixture.TestCatalogFixture;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;
import gravit.qa.sprint.config.SetupProps;
import gravit.qa.sprint.dto.internal.SetupDiffDto;
import gravit.qa.sprint.dto.internal.SetupFeatureSpecDto;
import gravit.qa.sprint.dto.internal.SetupPromptDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class SetupPromptBuilderTest {

    private static final int MAX_PROMPT_CHARS = 1000;
    private static final NotionSprintDto SPRINT = new NotionSprintDto("s", "3rd Agile", null, null, List.of("p"), "유닛 상세 개편", null, null, "{}");
    private static final List<FigmaScreenDto> SCREENS = List.of(new FigmaScreenDto("LRN-02", FigmaPlatform.WEB, List.of(
            new FigmaFrameDto("4:2", "LRN-02", null, "LEARNING", 1920.0),
            new FigmaFrameDto("4:5", "LRN-02/EMPTY", "EMPTY", "LEARNING", 1920.0)
    )));
    private static final SetupDiffDto DIFF = SetupDiffDto.of(
            Map.of("gravit-web", List.of(new GithubChangedFileDto("apps/web/src/Unit.tsx", "modified", 1, 1, 2, "@@", null))),
            List.of()
    );

    private final SetupPromptBuilder builder = new SetupPromptBuilder(
            TestCatalogFixture.LRN_02_테스트가_있는_카탈로그(),
            new LlmPromptTemplates(),
            new ObjectMapper(),
            new SetupProps(MAX_PROMPT_CHARS)
    );

    @Nested
    @DisplayName("프롬프트를 만들 때")
    class 생성 {

        @Test
        void 페르소나와_화면의_테스트_카탈로그는_시스템에_기능_근거는_사용자_프롬프트에_넣는다() {
            // when
            SetupPromptDto prompt = builder.build(SPRINT, 명세("진입하면 유닛 목록을 보여준다"), SCREENS, null, DIFF);

            // then
            assertSoftly(softly -> {
                softly.assertThat(prompt.truncated()).isFalse();
                softly.assertThat(prompt.prompt().system()).contains("- learner: ", "- admin: ", "- LRN-02.empty-state: 유닛 상세 빈 상태");
                softly.assertThat(prompt.prompt().user()).contains(
                        "화면 ID: LRN-02",
                        "파트: SERVER, WEB",
                        "명세 상태: CONFIRMED (확정)",
                        "진입하면 유닛 목록을 보여준다",
                        "- WEB: 프레임 2개, 상태 [EMPTY]",
                        "유닛 상세 개편",
                        "### gravit-web",
                        "- modified apps/web/src/Unit.tsx"
                );
                softly.assertThat(prompt.prompt().user()).doesNotContain("@@");
            });
        }

        @Test
        void 프레임과_기준점이_없으면_없다는_사실을_적는다() {
            // when
            SetupPromptDto prompt = builder.build(SPRINT, 명세("본문"), List.of(), null, SetupDiffDto.empty());

            // then
            assertSoftly(softly -> {
                softly.assertThat(prompt.prompt().user()).contains("이번 스프린트 Figma 프레임 없음");
                softly.assertThat(prompt.prompt().user()).contains("직전 기준점이 없어 변경 파일 목록이 없음");
            });
        }

        @Test
        void 상한을_넘으면_뒤에서_자르고_잘림을_표시한다() {
            // when
            SetupPromptDto prompt = builder.build(SPRINT, 명세("가".repeat(MAX_PROMPT_CHARS)), SCREENS, null, DIFF);

            // then
            assertSoftly(softly -> {
                softly.assertThat(prompt.truncated()).isTrue();
                softly.assertThat(prompt.prompt().user()).hasSize(MAX_PROMPT_CHARS);
                softly.assertThat(prompt.prompt().user()).endsWith(SetupPromptBuilder.TRUNCATION_NOTICE);
                softly.assertThat(prompt.prompt().user()).startsWith("아래 기능의 체크 항목 초안을 만들어라.");
                softly.assertThat(prompt.prompt().user()).doesNotContain("apps/web/src/Unit.tsx");
            });
        }
    }

    private static SetupFeatureSpecDto 명세(String markdown) {
        NotionSpecDto spec = new NotionSpecDto("p", "LRN-02", "유닛 상세", List.of("서버", "웹"), "/units", "getUnit", null, "확정", null, "{}");
        return SetupFeatureSpecDto.of(spec, List.of(Part.SERVER, Part.WEB), SpecStatus.CONFIRMED, markdown);
    }
}
