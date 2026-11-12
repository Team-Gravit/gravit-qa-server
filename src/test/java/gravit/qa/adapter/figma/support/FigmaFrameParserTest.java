package gravit.qa.adapter.figma.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.figma.domain.FigmaFrameWarningType;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameDto;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameWarningDto;
import gravit.qa.adapter.figma.dto.internal.FigmaQaFramesDto;
import gravit.qa.adapter.figma.dto.internal.FigmaScreenDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.FRAME_OUTSIDE_AREA;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.FRAME_OUTSIDE_PLATFORM;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.INVALID_FRAME_NAME;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.NESTED_SECTION;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.PLATFORM_WIDTH_MISMATCH;
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
    private static final String SPRINT_KEY = "3rd Agile";
    private static final String QA_PAGE = """
            {"id": "1:1", "name": "QA", "type": "CANVAS", "children": [
              {"id": "2:0", "name": "2nd Agile", "type": "SECTION", "children": [
                {"id": "2:1", "name": "WEB", "type": "SECTION", "children": [
                  {"id": "2:2", "name": "LEARNING", "type": "SECTION", "children": [
                    {"id": "2:3", "name": "LRN-01", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}}
                  ]}
                ]}
              ]},
              {"id": "3:0", "name": " 3rd Agile ", "type": "SECTION", "children": [
                {"id": "4:0", "name": "WEB", "type": "SECTION", "children": [
                  {"id": "4:1", "name": "LEARNING", "type": "SECTION", "children": [
                    {"id": "4:2", "name": "LRN-01", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}},
                    {"id": "4:3", "name": "LRN-02", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}},
                    {"id": "4:4", "name": "QUIZ-03/A", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}},
                    {"id": "4:5", "name": "Rectangle 12", "type": "RECTANGLE"}
                  ]},
                  {"id": "4:6", "name": "INTERVIEW SIMULATOR", "type": "SECTION", "children": [
                    {"id": "4:7", "name": "AI-08", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}},
                    {"id": "4:8", "name": "AI-08", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}},
                    {"id": "4:9", "name": "AI-08", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}},
                    {"id": "4:10", "name": "AI-HOME-01/DATA", "type": "FRAME", "absoluteBoundingBox": {"width": 1921.5}},
                    {"id": "4:11", "name": "미확인/INTERVIEW SIMULATOR-1", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}},
                    {"id": "4:12", "name": "최근 응시 이력 페이지", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}}
                  ]},
                  {"id": "4:13", "name": "OTHER", "type": "SECTION", "children": [
                    {"id": "4:14", "name": "묶음", "type": "SECTION", "children": [
                      {"id": "4:15", "name": "SETTING-01", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}}
                    ]}
                  ]},
                  {"id": "4:16", "name": "ARCHIVE", "type": "SECTION", "children": [
                    {"id": "4:17", "name": "LRN-02", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}}
                  ]},
                  {"id": "4:18", "name": "UP-02-01", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}}
                ]},
                {"id": "5:0", "name": "AOS", "type": "SECTION", "children": [
                  {"id": "5:1", "name": "MAIN & ONBOARDING", "type": "SECTION", "children": [
                    {"id": "5:2", "name": "ONB-01/A", "type": "FRAME", "absoluteBoundingBox": {"width": 360}},
                    {"id": "5:3", "name": "ONB-01/B", "type": "FRAME", "absoluteBoundingBox": {"width": 360}},
                    {"id": "5:4", "name": "ONB-01/C", "type": "FRAME", "absoluteBoundingBox": {"width": 358}}
                  ]},
                  {"id": "5:5", "name": "LEARNING", "type": "SECTION", "children": [
                    {"id": "5:6", "name": "LRN-01", "type": "FRAME", "absoluteBoundingBox": {"width": 360}}
                  ]},
                  {"id": "5:7", "name": "OTHER", "type": "SECTION", "children": [
                    {"id": "5:8", "name": "미확인/OTHER-1", "type": "FRAME", "absoluteBoundingBox": {"width": 360}},
                    {"id": "5:9", "name": "SETTING-01", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}}
                  ]}
                ]},
                {"id": "6:0", "name": "메모", "type": "SECTION", "children": []},
                {"id": "6:1", "name": "QUIZ-02/A", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}}
              ]},
              {"id": "7:0", "name": "SCREEN", "type": "SECTION", "children": [
                {"id": "7:1", "name": "LG-01", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}},
                {"id": "7:2", "name": "QUIZ-02/A", "type": "FRAME", "absoluteBoundingBox": {"width": 1920}}
              ]}
            ]}
            """;

    @Nested
    @DisplayName("스프린트 Section의 프레임을 해석할 때")
    class 프레임_해석 {

        @Test
        void 플랫폼과_화면_ID로_화면을_묶는다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), SPRINT_KEY);

            // then
            assertSoftly(softly -> {
                softly.assertThat(result.qaPageId()).isEqualTo("1:1");
                softly.assertThat(result.sprintSectionId()).isEqualTo("3:0");
                softly.assertThat(result.screens())
                        .extracting(FigmaScreenDto::platform, FigmaScreenDto::screenId)
                        .containsExactly(
                                tuple(WEB, "LRN-01"),
                                tuple(WEB, "LRN-02"),
                                tuple(WEB, "QUIZ-03"),
                                tuple(WEB, "AI-08"),
                                tuple(WEB, "AI-HOME-01"),
                                tuple(AOS, "ONB-01"),
                                tuple(AOS, "LRN-01"),
                                tuple(AOS, "SETTING-01")
                        );
            });
        }

        @Test
        void 프레임마다_이름_상태_구역_폭을_남긴다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), SPRINT_KEY);

            // then
            assertThat(result.findScreen(WEB, "QUIZ-03").orElseThrow().frames())
                    .extracting(FigmaFrameDto::nodeId, FigmaFrameDto::name, FigmaFrameDto::state, FigmaFrameDto::sectionName, FigmaFrameDto::width)
                    .containsExactly(tuple("4:4", "QUIZ-03/A", "A", "LEARNING", 1920.0));
        }

        @Test
        void 같은_화면_ID가_WEB과_AOS에_있으면_화면이_둘이다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), SPRINT_KEY);

            // then
            assertThat(result.findScreen(WEB, "LRN-01").orElseThrow().frames()).extracting(FigmaFrameDto::nodeId).containsExactly("4:2");
            assertThat(result.findScreen(AOS, "LRN-01").orElseThrow().frames()).extracting(FigmaFrameDto::nodeId).containsExactly("5:6");
        }

        @Test
        void 같은_이름의_프레임은_node_ID로_각각_남기고_상태로_보지_않는다() {
            // when
            FigmaScreenDto screen = FigmaFrameParser.parse("AbC123", json(QA_PAGE), SPRINT_KEY)
                    .findScreen(WEB, "AI-08")
                    .orElseThrow();

            // then
            assertThat(screen.frames()).extracting(FigmaFrameDto::nodeId).containsExactly("4:7", "4:8", "4:9");
            assertThat(screen.states()).isEmpty();
        }

        @Test
        void 기본_프레임_없이_상태_변형만_있어도_화면이_된다() {
            // when
            FigmaScreenDto screen = FigmaFrameParser.parse("AbC123", json(QA_PAGE), SPRINT_KEY)
                    .findScreen(AOS, "ONB-01")
                    .orElseThrow();

            // then
            assertThat(screen.frames()).extracting(FigmaFrameDto::nodeId).containsExactly("5:2", "5:3", "5:4");
            assertThat(screen.states()).containsExactly("A", "B", "C");
        }

        @Test
        void 스프린트_Section_밖의_프레임은_읽지_않는다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), SPRINT_KEY);

            // then
            assertThat(result.findScreen(WEB, "LG-01")).isEmpty();
            assertThat(result.screens())
                    .flatExtracting(FigmaScreenDto::frames)
                    .extracting(FigmaFrameDto::nodeId)
                    .doesNotContain("2:3", "7:1", "7:2");
        }

        @Test
        void 스프린트_Section이_없으면_빈_결과와_경고를_돌려준다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), "4th Agile");

            // then
            assertThat(result.screens()).isEmpty();
            assertThat(result.warnings()).extracting(FigmaFrameWarningDto::type).containsExactly(SPRINT_SECTION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("프레임 이름을 화면 ID와 상태로 나눌 때")
    class 이름_분해 {

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "QUIZ-03/A          | QUIZ-03 | A",
                "MY-04/MODAL/FOLLOW | MY-04   | MODAL/FOLLOW",
                "AI-08/질문 재생 중   | AI-08   | 질문 재생 중",
                "LRN-02             | LRN-02  |"
        })
        void 첫_슬래시_앞은_화면_ID_뒤는_상태다(
                String frameName,
                String screenId,
                String state
        ) {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", pageWithFrame(frameName, 1920.0), SPRINT_KEY);

            // then
            assertThat(result.screens()).extracting(FigmaScreenDto::screenId).containsExactly(screenId);
            assertThat(result.screens().getFirst().frames()).extracting(FigmaFrameDto::name, FigmaFrameDto::state)
                    .containsExactly(tuple(frameName, state));
            assertThat(result.warnings()).isEmpty();
        }

        @Test
        void 슬래시_앞뒤_공백은_잘라낸다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", pageWithFrame("MY-02 / EMPTY", 1920.0), SPRINT_KEY);

            // then
            assertThat(result.findScreen(WEB, "MY-02").orElseThrow().states()).containsExactly("EMPTY");
        }
    }

    @Nested
    @DisplayName("규칙을 벗어난 프레임과 Section을 만나면")
    class 경고 {

        @Test
        void 순회_순서대로_경고를_남긴다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), SPRINT_KEY);

            // then
            assertThat(result.warnings())
                    .extracting(FigmaFrameWarningDto::type, FigmaFrameWarningDto::platform, FigmaFrameWarningDto::nodeId, FigmaFrameWarningDto::name)
                    .containsExactly(
                            tuple(UNCONFIRMED_FRAME, WEB, "4:11", "미확인/INTERVIEW SIMULATOR-1"),
                            tuple(INVALID_FRAME_NAME, WEB, "4:12", "최근 응시 이력 페이지"),
                            tuple(NESTED_SECTION, WEB, "4:14", "묶음"),
                            tuple(UNKNOWN_SECTION, WEB, "4:16", "ARCHIVE"),
                            tuple(FRAME_OUTSIDE_AREA, WEB, "4:18", "UP-02-01"),
                            tuple(UNCONFIRMED_FRAME, AOS, "5:8", "미확인/OTHER-1"),
                            tuple(PLATFORM_WIDTH_MISMATCH, AOS, "5:9", "SETTING-01"),
                            tuple(UNKNOWN_SECTION, null, "6:0", "메모"),
                            tuple(FRAME_OUTSIDE_PLATFORM, null, "6:1", "QUIZ-02/A")
                    );
        }

        @Test
        void 미확인_규칙_밖_이름_구조_밖_프레임은_화면에_넣지_않는다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), SPRINT_KEY);

            // then
            assertThat(result.screens())
                    .flatExtracting(FigmaScreenDto::frames)
                    .extracting(FigmaFrameDto::nodeId)
                    .doesNotContain("4:11", "4:12", "4:15", "4:17", "4:18", "5:8", "6:1");
        }

        @Test
        void 폭이_플랫폼_기준을_벗어나도_화면에는_넣는다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", json(QA_PAGE), SPRINT_KEY);

            // then
            assertThat(result.findScreen(AOS, "SETTING-01").orElseThrow().frames())
                    .extracting(FigmaFrameDto::nodeId, FigmaFrameDto::width)
                    .containsExactly(tuple("5:9", 1920.0));
        }

        @ParameterizedTest
        @ValueSource(doubles = {1917.9, 1922.1, 360})
        void WEB_프레임_폭이_1920에서_2를_넘게_벗어나면_경고한다(double width) {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", pageWithFrame("LRN-02", width), SPRINT_KEY);

            // then
            assertThat(result.warnings()).extracting(FigmaFrameWarningDto::type).containsExactly(PLATFORM_WIDTH_MISMATCH);
            assertThat(result.findScreen(WEB, "LRN-02")).isPresent();
        }

        @Test
        void 폭을_알_수_없는_프레임도_폭_불일치로_경고한다() {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", pageWithFrame("LRN-02", null), SPRINT_KEY);

            // then
            assertThat(result.warnings()).extracting(FigmaFrameWarningDto::type).containsExactly(PLATFORM_WIDTH_MISMATCH);
            assertThat(result.findScreen(WEB, "LRN-02").orElseThrow().frames()).extracting(FigmaFrameDto::width).containsExactly((Double) null);
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "LRN-02/ | INVALID_FRAME_NAME",
                "/A      | INVALID_FRAME_NAME",
                "미확인    | UNCONFIRMED_FRAME"
        })
        void 이름을_나눌_수_없거나_미확인이면_화면에_넣지_않는다(
                String frameName,
                FigmaFrameWarningType type
        ) {
            // when
            FigmaQaFramesDto result = FigmaFrameParser.parse("AbC123", pageWithFrame(frameName, 1920.0), SPRINT_KEY);

            // then
            assertThat(result.screens()).isEmpty();
            assertThat(result.warnings()).extracting(FigmaFrameWarningDto::type, FigmaFrameWarningDto::name)
                    .containsExactly(tuple(type, frameName));
        }
    }

    @Nested
    @DisplayName("화면 ID 후보가 규칙에 맞는지 판단할 때")
    class 화면_ID_규칙 {

        @ParameterizedTest
        @ValueSource(strings = {"LRN-02", "UP-02-01", "AI-HOME-01", "UP-학습", "SETTING-01"})
        void 기능_코드_형식이면_화면_ID다(String name) {
            // when & then
            assertThat(FigmaFrameParser.isScreenId(name)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"최근 응시 이력 페이지", "L.7.1", "LRN-2", "lrn-02", "LRN-02-WEB", ""})
        void 형식을_벗어나면_화면_ID가_아니다(String name) {
            // when & then
            assertThat(FigmaFrameParser.isScreenId(name)).isFalse();
        }
    }

    @Test
    void 플랫폼과_구역_Section_중_잘린_것만_찾는다() {
        // given
        JsonNode qaPage = json("""
                {"id": "1:1", "type": "CANVAS", "children": [
                  {"id": "3:0", "name": "3rd Agile", "type": "SECTION", "children": [
                    {"id": "4:0", "name": "WEB", "type": "SECTION", "children": [
                      {"id": "4:1", "name": "LEARNING", "type": "SECTION"},
                      {"id": "4:2", "name": "ARCHIVE", "type": "SECTION"},
                      {"id": "4:3", "name": "MY", "type": "SECTION", "children": [
                        {"id": "4:4", "name": "묶음", "type": "SECTION"}
                      ]}
                    ]},
                    {"id": "5:0", "name": "AOS", "type": "SECTION"},
                    {"id": "6:0", "name": "메모", "type": "SECTION"}
                  ]},
                  {"id": "7:0", "name": "SCREEN", "type": "SECTION", "children": [
                    {"id": "7:1", "name": "LG-01", "type": "SECTION"}
                  ]}
                ]}
                """);

        // when & then
        assertThat(FigmaFrameParser.findTruncatedSections(qaPage, SPRINT_KEY))
                .extracting(node -> node.path("id").asText())
                .containsExactly("4:1", "5:0");
    }

    private static JsonNode pageWithFrame(
            String frameName,
            Double width
    ) {
        ObjectNode frame = OBJECT_MAPPER.createObjectNode()
                .put("id", "4:2")
                .put("name", frameName)
                .put("type", "FRAME");
        if (width != null) {
            frame.putObject("absoluteBoundingBox").put("width", width);
        }
        return json("""
                {"id": "1:1", "name": "QA", "type": "CANVAS", "children": [
                  {"id": "3:0", "name": "3rd Agile", "type": "SECTION", "children": [
                    {"id": "4:0", "name": "WEB", "type": "SECTION", "children": [
                      {"id": "4:1", "name": "LEARNING", "type": "SECTION", "children": [%s]}
                    ]}
                  ]}
                ]}
                """.formatted(frame));
    }

    private static JsonNode json(String value) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
