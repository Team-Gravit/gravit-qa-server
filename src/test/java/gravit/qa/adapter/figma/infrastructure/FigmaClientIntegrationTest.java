package gravit.qa.adapter.figma.infrastructure;

import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameDto;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameImagesDto;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameWarningDto;
import gravit.qa.adapter.figma.dto.internal.FigmaQaFramesDto;
import gravit.qa.adapter.figma.dto.internal.FigmaScreenDto;
import gravit.qa.support.HttpStubServer;
import gravit.qa.support.HttpStubServer.RecordedRequest;
import gravit.qa.support.QaSpringBootTest;
import gravit.qa.support.RecordingSleeper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.NESTED_SECTION;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.QA_PAGE_NOT_FOUND;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.UNCONFIRMED_FRAME;
import static gravit.qa.adapter.figma.domain.FigmaPlatform.AOS;
import static gravit.qa.adapter.figma.domain.FigmaPlatform.WEB;
import static gravit.qa.support.HttpStubServer.StubResponse.bytes;
import static gravit.qa.support.HttpStubServer.StubResponse.json;
import static gravit.qa.support.HttpStubServer.StubResponse.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
class FigmaClientIntegrationTest {

    private static final String FILE_KEY = "AbC123";
    private static final String FILE_PATH = "/v1/files/" + FILE_KEY;
    private static final String NODES_PATH = "/v1/files/" + FILE_KEY + "/nodes";
    private static final String IMAGES_PATH = "/v1/images/" + FILE_KEY;
    private static final String FILE_BODY = """
            {"name": "Gravit", "document": {"id": "0:0", "type": "DOCUMENT", "children": [
              {"id": "0:1", "name": "Screen Design", "type": "CANVAS"},
              {"id": "1:1", "name": "QA", "type": "CANVAS"}
            ]}}
            """;
    private static final String SPRINT_KEY = "3rd Agile";
    private static final String QA_PAGE_NODES = """
            {"nodes": {"1:1": {"document": {"id": "1:1", "name": "QA", "type": "CANVAS", "children": [
              {"id": "3:0", "name": "3rd Agile", "type": "SECTION", "children": [
                {"id": "4:0", "name": "WEB", "type": "SECTION", "children": [
                  {"id": "4:1", "name": "LEARNING", "type": "SECTION", "children": [
                    {"id": "4:2", "name": "LRN-02", "type": "FRAME", "absoluteBoundingBox": {"x": 0, "y": 0, "width": 1920, "height": 1080}}
                  ]},
                  {"id": "4:3", "name": "MY", "type": "SECTION", "children": [
                    {"id": "4:4", "name": "묶음", "type": "SECTION"}
                  ]}
                ]},
                {"id": "5:0", "name": "AOS", "type": "SECTION", "children": [
                  {"id": "5:1", "name": "LEARNING", "type": "SECTION", "children": [
                    {"id": "5:2", "name": "LRN-02", "type": "FRAME", "absoluteBoundingBox": {"x": 0, "y": 0, "width": 360, "height": 740}}
                  ]},
                  {"id": "5:3", "name": "OTHER", "type": "SECTION"}
                ]}
              ]},
              {"id": "7:0", "name": "SCREEN", "type": "SECTION", "children": [
                {"id": "7:1", "name": "LG-01", "type": "SECTION"}
              ]}
            ]}}}}
            """;
    private static final String OTHER_SECTION_NODES = """
            {"nodes": {"5:3": {"document": {"id": "5:3", "name": "OTHER", "type": "SECTION", "children": [
              {"id": "5:4", "name": "SETTING-01", "type": "FRAME", "absoluteBoundingBox": {"x": 0, "y": 0, "width": 360, "height": 740}},
              {"id": "5:5", "name": "SETTING-01", "type": "FRAME", "absoluteBoundingBox": {"x": 400, "y": 0, "width": 360, "height": 740}},
              {"id": "5:6", "name": "미확인/OTHER-1", "type": "FRAME", "absoluteBoundingBox": {"x": 800, "y": 0, "width": 360, "height": 740}}
            ]}}}}
            """;

    @Autowired
    private FigmaClient figmaClient;

    @Autowired
    private HttpStubServer externalApiStubServer;

    @Autowired
    private RecordingSleeper recordingSleeper;

    @Nested
    @DisplayName("QA 페이지 프레임을 수집할 때")
    class 프레임_수집 {

        @Test
        void QA_페이지를_찾아_스프린트_프레임을_해석하고_토큰_헤더를_보낸다() {
            // given
            externalApiStubServer.stub("GET", FILE_PATH, json(200, FILE_BODY));
            externalApiStubServer.stub("GET", NODES_PATH, json(200, QA_PAGE_NODES), json(200, OTHER_SECTION_NODES));

            // when
            FigmaQaFramesDto result = figmaClient.getQaFrames(FILE_KEY, SPRINT_KEY);

            // then
            List<RecordedRequest> nodeRequests = externalApiStubServer.requests("GET", NODES_PATH);
            assertSoftly(softly -> {
                softly.assertThat(result.screens())
                        .extracting(FigmaScreenDto::platform, FigmaScreenDto::screenId)
                        .containsExactly(tuple(WEB, "LRN-02"), tuple(AOS, "LRN-02"), tuple(AOS, "SETTING-01"));
                softly.assertThat(result.findScreen(AOS, "SETTING-01").orElseThrow().frames())
                        .extracting(FigmaFrameDto::nodeId, FigmaFrameDto::state, FigmaFrameDto::sectionName, FigmaFrameDto::width)
                        .containsExactly(tuple("5:4", null, "OTHER", 360.0), tuple("5:5", null, "OTHER", 360.0));
                softly.assertThat(result.warnings())
                        .extracting(FigmaFrameWarningDto::type, FigmaFrameWarningDto::name)
                        .containsExactly(tuple(NESTED_SECTION, "묶음"), tuple(UNCONFIRMED_FRAME, "미확인/OTHER-1"));
                softly.assertThat(decodedQuery(externalApiStubServer.requests("GET", FILE_PATH).getFirst())).isEqualTo("depth=1");
                softly.assertThat(nodeRequests).extracting(FigmaClientIntegrationTest::decodedQuery)
                        .containsExactly("ids=1:1&depth=4", "ids=5:3&depth=2");
                softly.assertThat(externalApiStubServer.requests()).allSatisfy(
                        request -> assertThat(request.header("X-Figma-Token")).isEqualTo("test-figma-token"));
            });
        }

        @Test
        void QA_페이지가_없으면_노드를_조회하지_않고_경고를_돌려준다() {
            // given
            externalApiStubServer.stub("GET", FILE_PATH, json(200, FILE_BODY.replace("\"QA\"", "\"Archive\"")));

            // when
            FigmaQaFramesDto result = figmaClient.getQaFrames(FILE_KEY, SPRINT_KEY);

            // then
            assertThat(result.warnings()).extracting(FigmaFrameWarningDto::type).containsExactly(QA_PAGE_NOT_FOUND);
            assertThat(externalApiStubServer.requests("GET", NODES_PATH)).isEmpty();
        }

        @Test
        void 파일_키_형식이_아니면_호출하지_않고_재시도하지_않는_실패() {
            // when & then
            assertThatThrownBy(() -> figmaClient.getQaFrames("../etc", SPRINT_KEY))
                    .isInstanceOf(NonRetryableExternalApiException.class);
            assertThat(externalApiStubServer.requests()).isEmpty();
        }
    }

    @Nested
    @DisplayName("프레임 PNG를 내보낼 때")
    class PNG_내보내기 {

        @Test
        void 렌더된_이미지를_토큰_없이_내려받고_실패한_노드는_따로_돌려준다() {
            // given
            byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
            String imageUrl = externalApiStubServer.baseUrl() + "/s3/4-2.png";
            externalApiStubServer.stub("GET", IMAGES_PATH, json(200, "{\"err\": null, \"images\": {\"4:2\": \"" + imageUrl + "\", \"5:2\": null}}"));
            externalApiStubServer.stub("GET", "/s3/4-2.png", bytes("image/png", png));

            // when
            FigmaFrameImagesDto images = figmaClient.exportPng(FILE_KEY, List.of("4:2", "5:2", "4:2"));

            // then
            RecordedRequest download = externalApiStubServer.requests("GET", "/s3/4-2.png").getFirst();
            assertSoftly(softly -> {
                softly.assertThat(images.nodeIdToPng()).containsOnlyKeys("4:2");
                softly.assertThat(images.nodeIdToPng().get("4:2")).isEqualTo(png);
                softly.assertThat(images.failedNodeIds()).containsExactly("5:2");
                softly.assertThat(decodedQuery(externalApiStubServer.requests("GET", IMAGES_PATH).getFirst()))
                        .isEqualTo("ids=4:2,5:2&format=png&scale=1");
                softly.assertThat(download.hasHeader("X-Figma-Token")).isFalse();
            });
        }
    }

    @Nested
    @DisplayName("재시도 규칙에 따라")
    class 재시도 {

        @Test
        void 사용_한도_429는_Retry_After만큼_기다린_뒤_재시도한다() {
            // given
            externalApiStubServer.stub("GET", FILE_PATH, status(429).withHeader("Retry-After", "3"), json(200, FILE_BODY.replace("\"QA\"", "\"Archive\"")));

            // when
            figmaClient.getQaFrames(FILE_KEY, SPRINT_KEY);

            // then
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(3));
        }

        @Test
        void 토큰_오류_403은_재시도하지_않고_토큰을_메시지에_남기지_않는다() {
            // given
            externalApiStubServer.stub("GET", FILE_PATH, status(403));

            // when & then
            assertThatThrownBy(() -> figmaClient.getQaFrames(FILE_KEY, SPRINT_KEY))
                    .isInstanceOf(NonRetryableExternalApiException.class)
                    .hasMessageNotContaining("test-figma-token");
            assertThat(externalApiStubServer.requests()).hasSize(1);
        }
    }

    private static String decodedQuery(RecordedRequest request) {
        return URLDecoder.decode(request.query(), StandardCharsets.UTF_8);
    }
}
