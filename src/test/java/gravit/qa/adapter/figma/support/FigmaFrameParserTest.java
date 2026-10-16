package gravit.qa.adapter.figma.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameDto;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameWarningDto;
import gravit.qa.adapter.figma.dto.internal.FigmaQaFramesDto;
import gravit.qa.adapter.figma.dto.internal.FigmaScreenDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.FRAME_OUTSIDE_PLATFORM;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.INVALID_FRAME_NAME;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.SPRINT_SECTION_NOT_FOUND;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.UNCONFIRMED_FRAME;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.UNKNOWN_SECTION;
import static gravit.qa.adapter.figma.domain.FigmaPlatform.AOS;
import static gravit.qa.adapter.figma.domain.FigmaPlatform.WEB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class FigmaFrameParserTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String QA_PAGE = """
            {"id": "1:1", "name": "QA", "type": "CANVAS", "children": [
              {"id": "2:0", "name": "2026-S20", "type": "SECTION", "children": [
                {"id": "2:1", "name": "WEB", "type": "SECTION", "children": [{"id": "2:2", "name": "O.1", "type": "FRAME"}]}
              ]},
              {"id": "3:0", "name": " 2026-S21 ", "type": "SECTION", "children": [
                {"id": "4:0", "name": "WEB", "type": "SECTION", "children": [
                  {"id": "4:1", "name": "LEARNING", "type": "SECTION", "children": [
                    {"id": "4:2", "name": "L.7.1", "type": "FRAME"},
                    {"id": "4:3", "name": "L.7.2", "type": "FRAME"},
                    {"id": "4:4", "name": "Rectangle 12", "type": "RECTANGLE"}
                  ]},
                  {"id": "4:5", "name": "MY.2-1", "type": "FRAME"}
                ]},
                {"id": "5:0", "name": "AOS", "type": "SECTION", "children": [
                  {"id": "5:1", "name": "LEARNING", "type": "SECTION", "children": [
                    {"id": "5:2", "name": "L.7.1", "type": "FRAME"}
                  ]},
                  {"id": "5:3", "name": "LEAGUE", "type": "SECTION", "children": [
                    {"id": "5:4", "name": "LG.2", "type": "FRAME"},
                    {"id": "5:5", "name": "LG.2", "type": "FRAME"},
                    {"id": "5:6", "name": "미확인/LEAGUE-1", "type": "FRAME"},
                    {"id": "5:7", "name": "LG.2_실버", "type": "FRAME"},
                    {"id": "5:8", "name": "Frame 123", "type": "FRAME"}
                  ]}
                ]},
                {"id": "6:0", "name": "메모", "type": "SECTION", "children": []},
                {"id": "6:1", "name": "L.9", "type": "FRAME"}
              ]}
            ]}
            """;

    @Nested
    @DisplayName("스프린트 Section의 프레임을 해석할 때")
    class 프레임_해석 {

        @Test
        void 화면_ID인_프레임을_플랫폼별로_모은다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), "2026-S21");

            // then
            assertSoftly(softly -> {
                softly.assertThat(result.qaPageId()).isEqualTo("1:1");
                softly.assertThat(result.sprintSectionId()).isEqualTo("3:0");
                softly.assertThat(result.screens())
                        .extracting(FigmaScreenDto::platform, FigmaScreenDto::screenId)
                        .containsExactly(
                                tuple(WEB, "L.7.1"),
                                tuple(WEB, "L.7.2"),
                                tuple(WEB, "MY.2-1"),
                                tuple(AOS, "L.7.1"),
                                tuple(AOS, "LG.2")
                        );
                softly.assertThat(result.findScreen(WEB, "L.7.1").orElseThrow().frames())
                        .extracting(FigmaFrameDto::nodeId, FigmaFrameDto::sectionName)
                        .containsExactly(tuple("4:2", "LEARNING"));
            });
        }

        @Test
        void WEB과_AOS의_같은_ID는_플랫폼별로_따로_남는다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), "2026-S21");

            // then
            assertThat(result.findScreen(WEB, "L.7.1").orElseThrow().frames()).extracting(FigmaFrameDto::nodeId).containsExactly("4:2");
            assertThat(result.findScreen(AOS, "L.7.1").orElseThrow().frames()).extracting(FigmaFrameDto::nodeId).containsExactly("5:2");
        }

        @Test
        void 같은_ID가_여러_개면_상태_변형으로_묶는다() {
            // when
            FigmaScreenDto screen = FigmaFrameParser.parse("AbC123", json(QA_PAGE), "2026-S21")
                    .findScreen(AOS, "LG.2")
                    .orElseThrow();

            // then
            assertThat(screen.hasStateVariants()).isTrue();
            assertThat(screen.frames()).extracting(FigmaFrameDto::nodeId).containsExactly("5:4", "5:5");
        }

        @Test
        void 미확인_프레임과_규칙_밖_이름을_경고로_남긴다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), "2026-S21");

            // then
            assertThat(result.warnings())
                    .extracting(FigmaFrameWarningDto::type, FigmaFrameWarningDto::platform, FigmaFrameWarningDto::name)
                    .containsExactly(
                            tuple(UNCONFIRMED_FRAME, AOS, "미확인/LEAGUE-1"),
                            tuple(INVALID_FRAME_NAME, AOS, "LG.2_실버"),
                            tuple(INVALID_FRAME_NAME, AOS, "Frame 123"),
                            tuple(UNKNOWN_SECTION, null, "메모"),
                            tuple(FRAME_OUTSIDE_PLATFORM, null, "L.9")
                    );
        }

        @Test
        void 스프린트_Section이_없으면_빈_결과와_경고를_돌려준다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), "2026-S99");

            // then
            assertThat(result.screens()).isEmpty();
            assertThat(result.warnings()).extracting(FigmaFrameWarningDto::type).containsExactly(SPRINT_SECTION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("프레임 이름이 화면 ID인지 판단할 때")
    class 화면_ID_규칙 {

        @ParameterizedTest
        @ValueSource(strings = {"O.1", "M.2.3", "L.7.1", "LG.2", "MY.2-1", "A.10.2-3", "E.1", "W.3", "R.4.1.2"})
        void 접두어와_번호_형식이면_화면_ID다(String name) {
            // when & then
            assertThat(FigmaFrameParser.isScreenId(name)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"LRN-03", "L7.1", "L.7.", "X.1", "l.7.1", "L.7.1_WEB", "L.7.1 빈 상태", "미확인/LEARNING-1", ""})
        void 형식을_벗어나면_화면_ID가_아니다(String name) {
            // when & then
            assertThat(FigmaFrameParser.isScreenId(name)).isFalse();
        }
    }

    @Test
    void 잘린_하위_Section을_찾는다() {
        // given
        JsonNode qaPage = json("""
                {"id": "1:1", "type": "CANVAS", "children": [
                  {"id": "3:0", "name": "2026-S21", "type": "SECTION", "children": [
                    {"id": "4:0", "name": "WEB", "type": "SECTION", "children": [
                      {"id": "4:1", "name": "LEARNING", "type": "SECTION"}
                    ]}
                  ]}
                ]}
                """);

        // when & then
        assertThat(FigmaFrameParser.findTruncatedSections(qaPage, "2026-S21"))
                .extracting(node -> node.path("id").asText())
                .containsExactly("4:1");
    }

    private static JsonNode json(String value) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
