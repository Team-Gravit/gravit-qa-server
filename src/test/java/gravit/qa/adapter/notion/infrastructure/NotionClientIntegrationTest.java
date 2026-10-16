package gravit.qa.adapter.notion.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.notion.dto.internal.NotionPageBodyDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecDto;
import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.support.HttpStubServer;
import gravit.qa.support.HttpStubServer.RecordedRequest;
import gravit.qa.support.QaSpringBootTest;
import gravit.qa.support.RecordingSleeper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static gravit.qa.support.HttpStubServer.StubResponse.json;
import static gravit.qa.support.HttpStubServer.StubResponse.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
class NotionClientIntegrationTest {

    private static final String SPRINT_ID = "11111111111111111111111111111111";
    private static final String SPEC_ID = "22222222222222222222222222222222";
    private static final String SECOND_SPEC_ID = "33333333333333333333333333333333";
    private static final String SPRINT_DB = "4e533895-42c3-4a33-9a85-6820e77ccefc";
    private static final String SPEC_DB = "43fc286e-87ce-4ae3-862e-abf24d9096b3";
    private static final String SPRINT_PATH = "/v1/pages/" + SPRINT_ID;

    @Autowired
    private NotionClient notionClient;

    @Autowired
    private HttpStubServer externalApiStubServer;

    @Autowired
    private RecordingSleeper recordingSleeper;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("스프린트 행을 조회할 때")
    class 스프린트_조회 {

        @Test
        void 가이드_3_1의_속성을_읽고_토큰과_API_버전_헤더를_보낸다() {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, json(200, sprintPage(SPRINT_DB, false)));

            // when
            NotionSprintDto sprint = notionClient.getSprint("https://www.notion.so/gravit/2026-S21-" + SPRINT_ID + "?pvs=4");

            // then
            RecordedRequest request = externalApiStubServer.requests().getFirst();
            assertSoftly(softly -> {
                softly.assertThat(sprint.pageId()).isEqualTo(SPRINT_ID);
                softly.assertThat(sprint.name()).isEqualTo("2026-S21");
                softly.assertThat(sprint.periodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
                softly.assertThat(sprint.periodEnd()).isEqualTo(LocalDate.of(2026, 10, 14));
                softly.assertThat(sprint.targetFeaturePageIds()).containsExactly(SPEC_ID);
                softly.assertThat(sprint.changeSummary()).isEqualTo("유닛 상세 개편");
                softly.assertThat(sprint.figmaQaPageUrl()).isEqualTo("https://www.figma.com/design/AbC123/Gravit?node-id=1-1");
                softly.assertThat(sprint.qaBaseline()).isEmpty();
                softly.assertThat(request.header("Authorization")).isEqualTo("Bearer test-notion-token");
                softly.assertThat(request.header("Notion-Version")).isEqualTo("2022-06-28");
            });
        }

        @Test
        void 대상_기능이_25개를_넘으면_속성_항목_API로_relation을_끝까지_따라간다() {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, json(200, sprintPage(SPRINT_DB, true)));
            externalApiStubServer.stub("GET", SPRINT_PATH + "/properties/rel%3Ax",
                    json(200, "{\"results\": [{\"relation\": {\"id\": \"" + SPEC_ID + "\"}}], \"has_more\": true, \"next_cursor\": \"cursor-2\"}"),
                    json(200, "{\"results\": [{\"relation\": {\"id\": \"" + SECOND_SPEC_ID + "\"}}], \"has_more\": false, \"next_cursor\": null}"));

            // when
            NotionSprintDto sprint = notionClient.getSprint(SPRINT_ID);

            // then
            assertThat(sprint.targetFeaturePageIds()).containsExactly(SPEC_ID, SECOND_SPEC_ID);
            assertThat(externalApiStubServer.requests("GET", SPRINT_PATH + "/properties/rel%3Ax"))
                    .extracting(RecordedRequest::query)
                    .containsExactly("page_size=100", "page_size=100&start_cursor=cursor-2");
        }

        @Test
        void 스프린트_DB의_행이_아니면_재시도하지_않는_실패() {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, json(200, sprintPage(SPEC_DB, false)));

            // when & then
            assertThatThrownBy(() -> notionClient.getSprint(SPRINT_ID))
                    .isInstanceOf(NonRetryableExternalApiException.class)
                    .hasMessageContaining("스프린트 DB");
        }
    }

    @Nested
    @DisplayName("기능 명세와 본문을 조회할 때")
    class 기능_명세_조회 {

        @Test
        void 대상_기능의_명세_속성을_읽는다() {
            // given
            externalApiStubServer.stub("GET", "/v1/pages/" + SPEC_ID, json(200, specPage()));
            NotionSprintDto sprint = new NotionSprintDto(SPRINT_ID, "2026-S21", null, null, List.of(SPEC_ID), null, null, null, "{}");

            // when
            List<NotionSpecDto> specs = notionClient.getTargetSpecs(sprint);

            // then
            NotionSpecDto spec = specs.getFirst();
            assertSoftly(softly -> {
                softly.assertThat(spec.screenId()).isEqualTo("L.7.1");
                softly.assertThat(spec.name()).isEqualTo("유닛 상세");
                softly.assertThat(spec.parts()).containsExactly("서버", "웹");
                softly.assertThat(spec.route()).isEqualTo("/learning/units/$unitId");
                softly.assertThat(spec.relatedOperationIds()).containsExactly("getUnit", "getLessons");
                softly.assertThat(spec.specStatus()).isEqualTo("확정");
                softly.assertThat(spec.lastVerifiedAt()).isEqualTo(LocalDate.of(2026, 10, 2));
            });
        }

        @Test
        void 본문_블록을_페이지와_하위_블록까지_모아_마크다운으로_만든다() {
            // given
            String childrenPath = "/v1/blocks/" + SPEC_ID + "/children";
            externalApiStubServer.stub("GET", childrenPath,
                    json(200, """
                            {"results": [
                              {"id": "a1", "type": "heading_2", "has_children": false, "heading_2": {"rich_text": [{"plain_text": "목적"}]}},
                              {"id": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "type": "bulleted_list_item", "has_children": true,
                               "bulleted_list_item": {"rich_text": [{"plain_text": "유닛 진입"}]}}
                            ], "has_more": true, "next_cursor": "cursor-2"}
                            """),
                    json(200, """
                            {"results": [
                              {"id": "a3", "type": "paragraph", "has_children": false, "paragraph": {"rich_text": [{"plain_text": "끝"}]}}
                            ], "has_more": false, "next_cursor": null}
                            """));
            externalApiStubServer.stub("GET", "/v1/blocks/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/children", json(200, """
                    {"results": [
                      {"id": "b1", "type": "to_do", "has_children": false, "to_do": {"checked": true, "rich_text": [{"plain_text": "빈 상태"}]}}
                    ], "has_more": false, "next_cursor": null}
                    """));

            // when
            NotionPageBodyDto body = notionClient.getPageBody(SPEC_ID);

            // then
            assertThat(body.markdown()).isEqualTo("## 목적\n- 유닛 진입\n  - [x] 빈 상태\n끝");
            assertThat(body.blocks()).hasSize(3);
            assertThat(body.rawJson()).contains("\"children\"");
        }
    }

    @Nested
    @DisplayName("QA 기준점을 기록할 때")
    class 기준점_기록 {

        @Test
        void 스프린트_행인지_확인한_뒤_QA_기준점_속성만_PATCH한다() throws Exception {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, json(200, sprintPage(SPRINT_DB, false)));
            externalApiStubServer.stub("PATCH", SPRINT_PATH, json(200, sprintPage(SPRINT_DB, false)));

            // when
            notionClient.updateSprintBaseline(SPRINT_ID, "gravit-server@1111111");

            // then
            RecordedRequest patch = externalApiStubServer.requests("PATCH", SPRINT_PATH).getFirst();
            JsonNode body = objectMapper.readTree(patch.body());
            assertSoftly(softly -> {
                softly.assertThat(body.size()).isEqualTo(1);
                softly.assertThat(body.path("properties").size()).isEqualTo(1);
                softly.assertThat(body.at("/properties/QA 기준점/rich_text/0/text/content").asText()).isEqualTo("gravit-server@1111111");
                softly.assertThat(patch.header("Content-Type")).startsWith("application/json");
            });
        }

        @Test
        void 기능_명세_행에는_기록하지_않는다() {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, json(200, sprintPage(SPEC_DB, false)));

            // when & then
            assertThatThrownBy(() -> notionClient.updateSprintBaseline(SPRINT_ID, "x"))
                    .isInstanceOf(NonRetryableExternalApiException.class);
            assertThat(externalApiStubServer.requests("PATCH", SPRINT_PATH)).isEmpty();
        }
    }

    @Nested
    @DisplayName("재시도 규칙에 따라")
    class 재시도 {

        @Test
        void 사용_한도_429는_Retry_After만큼_기다린_뒤_재시도한다() {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, status(429).withHeader("Retry-After", "2"), json(200, sprintPage(SPRINT_DB, false)));

            // when
            NotionSprintDto sprint = notionClient.getSprint(SPRINT_ID);

            // then
            assertThat(sprint.name()).isEqualTo("2026-S21");
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(2));
        }

        @Test
        void 인증_실패_401은_재시도하지_않고_토큰을_메시지에_남기지_않는다() {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, status(401));

            // when & then
            assertThatThrownBy(() -> notionClient.getSprint(SPRINT_ID))
                    .isInstanceOf(NonRetryableExternalApiException.class)
                    .hasMessageNotContaining("test-notion-token");
            assertThat(externalApiStubServer.requests()).hasSize(1);
        }

        @Test
        void 없는_페이지_404는_재시도하지_않는다() {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, status(404));

            // when & then
            assertThatThrownBy(() -> notionClient.getSprint(SPRINT_ID))
                    .isInstanceOf(NonRetryableExternalApiException.class);
            assertThat(externalApiStubServer.requests()).hasSize(1);
        }
    }

    private static String sprintPage(
            String parentDatabaseId,
            boolean relationHasMore
    ) {
        return """
                {"object": "page", "id": "11111111-1111-1111-1111-111111111111",
                 "parent": {"type": "database_id", "database_id": "%s"},
                 "properties": {
                   "이름": {"id": "title", "type": "title", "title": [{"plain_text": "2026-S21"}]},
                   "기간": {"id": "p", "type": "date", "date": {"start": "2026-10-01", "end": "2026-10-14"}},
                   "대상 기능": {"id": "rel%%3Ax", "type": "relation", "relation": [{"id": "22222222-2222-2222-2222-222222222222"}], "has_more": %s},
                   "변경 요약": {"id": "s", "type": "rich_text", "rich_text": [{"plain_text": "유닛 상세 개편"}]},
                   "Figma QA 페이지": {"id": "f", "type": "url", "url": "https://www.figma.com/design/AbC123/Gravit?node-id=1-1"},
                   "QA 기준점": {"id": "b", "type": "rich_text", "rich_text": []}
                 }}
                """.formatted(parentDatabaseId, relationHasMore);
    }

    private static String specPage() {
        return """
                {"object": "page", "id": "22222222-2222-2222-2222-222222222222",
                 "parent": {"type": "database_id", "database_id": "%s"},
                 "properties": {
                   "화면 ID": {"id": "title", "type": "title", "title": [{"plain_text": "L.7.1"}]},
                   "이름": {"id": "n", "type": "rich_text", "rich_text": [{"plain_text": "유닛 상세"}]},
                   "파트": {"id": "p", "type": "multi_select", "multi_select": [{"name": "서버"}, {"name": "웹"}]},
                   "라우트": {"id": "r", "type": "rich_text", "rich_text": [{"plain_text": "/learning/units/$unitId"}]},
                   "관련 API": {"id": "a", "type": "rich_text", "rich_text": [{"plain_text": "getUnit, getLessons\\ngetUnit"}]},
                   "Figma": {"id": "f", "type": "url", "url": null},
                   "명세 상태": {"id": "s", "type": "select", "select": {"name": "확정"}},
                   "최종 검증일": {"id": "d", "type": "date", "date": {"start": "2026-10-02", "end": null}}
                 }}
                """.formatted(SPEC_DB);
    }
}
