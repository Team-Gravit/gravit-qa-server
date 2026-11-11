package gravit.qa.sprint.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.support.HttpStubServer;
import gravit.qa.support.HttpStubServer.StubResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.글자;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.링크;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.목록;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.문단;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.본문_응답;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.코드;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.표_블록;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.행;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.행_응답;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.헤더_행;
import static gravit.qa.support.HttpStubServer.StubResponse.bytes;
import static gravit.qa.support.HttpStubServer.StubResponse.json;

public final class SetupStubFixture {

    public static final String SPRINT_PAGE_ID = "11111111111111111111111111111111";
    public static final String LRN_PAGE_ID = "22222222222222222222222222222222";
    public static final String MY_PAGE_ID = "33333333333333333333333333333333";
    public static final String LRN_TABLE_ID = "2222aaaa2222aaaa2222aaaa2222aaaa";
    public static final String MY_TABLE_ID = "3333aaaa3333aaaa3333aaaa3333aaaa";
    public static final String LRN_BODY_TEXT = "진입하면 유닛 목록을 보여준다";
    public static final List<String> LRN_APIS = List.of("GET /api/v1/units/{unitId}", "GET /api/v1/missions");
    public static final String SPRINT_KEY = "3rd Agile";
    public static final String FILE_KEY = "AbC123";
    public static final String FIGMA_URL = "https://www.figma.com/design/" + FILE_KEY + "/Gravit?node-id=1-1";
    public static final String SERVER_SHA = "a".repeat(40);
    public static final String WEB_SHA = "b".repeat(40);
    public static final String AOS_SHA = "c".repeat(40);
    public static final String COMPLETIONS_PATH = "/v1/chat/completions";
    public static final String SPRINT_PATH = "/v1/pages/" + SPRINT_PAGE_ID;
    public static final String OPENAPI_PATH = "/v3/api-docs";

    private static final String SPRINT_DB = "4e533895-42c3-4a33-9a85-6820e77ccefc";
    private static final String SPEC_DB = "43fc286e-87ce-4ae3-862e-abf24d9096b3";
    private static final String OWNER_PATH = "/repos/Team-Gravit/";
    private static final String FIGMA_FILE_PATH = "/v1/files/" + FILE_KEY;
    private static final String FIGMA_NODES_PATH = "/v1/files/" + FILE_KEY + "/nodes";
    private static final String FIGMA_IMAGES_PATH = "/v1/images/" + FILE_KEY;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String EMPTY_VALUE = "-";
    private static final String PART_DELIMITER = ", ";
    private static final String FIGMA_FRAME_URL = "https://www.figma.com/design/" + FILE_KEY + "/?node-id=4-2";
    private static final String QA_PAGE_NODES = """
            {"nodes": {"1:1": {"document": {"id": "1:1", "name": "QA", "type": "CANVAS", "children": [
              {"id": "3:0", "name": "3rd Agile", "type": "SECTION", "children": [
                {"id": "4:0", "name": "WEB", "type": "SECTION", "children": [
                  {"id": "4:1", "name": "LEARNING", "type": "SECTION", "children": [
                    {"id": "4:2", "name": "LRN-02", "type": "FRAME", "absoluteBoundingBox": {"x": 0, "y": 0, "width": 1920, "height": 1080}},
                    {"id": "4:5", "name": "LRN-02/EMPTY", "type": "FRAME", "absoluteBoundingBox": {"x": 2000, "y": 0, "width": 1920, "height": 1080}}
                  ]},
                  {"id": "4:3", "name": "MY", "type": "SECTION", "children": []}
                ]},
                {"id": "5:0", "name": "AOS", "type": "SECTION", "children": [
                  {"id": "5:1", "name": "LEARNING", "type": "SECTION", "children": [
                    {"id": "5:2", "name": "LRN-02", "type": "FRAME", "absoluteBoundingBox": {"x": 0, "y": 0, "width": 360, "height": 740}}
                  ]}
                ]}
              ]}
            ]}}}}
            """;
    private static final String FIGMA_FILE = """
            {"name": "Gravit", "document": {"id": "0:0", "type": "DOCUMENT", "children": [
              {"id": "1:1", "name": "QA", "type": "CANVAS"}
            ]}}
            """;
    private static final String API_DOCS = """
            {"openapi": "3.1.0", "paths": {"/api/v1/units/{unitId}": {"get": {"operationId": "getUnit", "responses": {"200": {"description": "ok"}}}}}}
            """;

    private SetupStubFixture() {
    }

    public static void 외부_응답_등록(
            HttpStubServer server,
            int aosVersionCode
    ) {
        Notion_응답_등록(server, 스프린트_페이지(List.of(LRN_PAGE_ID, MY_PAGE_ID), FIGMA_URL));
        Figma_응답_등록(server);
        GitHub_응답_등록(server, aosVersionCode);
        server.stub("GET", OPENAPI_PATH, json(200, API_DOCS));
    }

    public static void Notion_응답_등록(
            HttpStubServer server,
            String sprintPage
    ) {
        server.stub("GET", SPRINT_PATH, json(200, sprintPage));
        server.stub("GET", "/v1/pages/" + LRN_PAGE_ID, json(200, 명세_페이지(LRN_PAGE_ID, "LRN-02")));
        server.stub("GET", "/v1/pages/" + MY_PAGE_ID, json(200, 명세_페이지(MY_PAGE_ID, "MY-04")));
        server.stub("GET", "/v1/blocks/" + LRN_PAGE_ID + "/children", json(200, 명세_본문(LRN_TABLE_ID, LRN_BODY_TEXT)));
        server.stub("GET", "/v1/blocks/" + MY_PAGE_ID + "/children", json(200, 명세_본문(MY_TABLE_ID, "팔로우 목록을 보여준다")));
        server.stub("GET", "/v1/blocks/" + LRN_TABLE_ID + "/children", json(200, 기본_정보_표("확정", List.of("서버", "웹"), "/learning/units/$unitId", LRN_APIS)));
        server.stub("GET", "/v1/blocks/" + MY_TABLE_ID + "/children", json(200, 기본_정보_표("확인 필요", List.of("웹"), null, List.of())));
    }

    public static void Figma_응답_등록(HttpStubServer server) {
        String imageBase = server.baseUrl() + "/s3/";
        server.stub("GET", FIGMA_FILE_PATH, json(200, FIGMA_FILE));
        server.stub("GET", FIGMA_NODES_PATH, json(200, QA_PAGE_NODES));
        server.stub("GET", FIGMA_IMAGES_PATH, json(200, "{\"err\": null, \"images\": {\"4:2\": \"" + imageBase + "4-2.png\", \"4:5\": \"" + imageBase + "4-5.png\"}}"));
        server.stub("GET", "/s3/4-2.png", bytes("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        server.stub("GET", "/s3/4-5.png", bytes("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '2'}));
    }

    public static void GitHub_응답_등록(
            HttpStubServer server,
            int aosVersionCode
    ) {
        server.stub("GET", OWNER_PATH + "gravit-server/branches/release", json(200, 브랜치(SERVER_SHA)));
        server.stub("GET", OWNER_PATH + "gravit-web/branches/release", json(200, 브랜치(WEB_SHA)));
        server.stub("GET", OWNER_PATH + "gravit-aos/branches/release", json(200, 브랜치(AOS_SHA)));

        String gradle = "android {\n  defaultConfig {\n    versionCode = " + aosVersionCode + "\n    versionName = \"1.0." + aosVersionCode + "\"\n  }\n}\n";
        String encoded = Base64.getEncoder().encodeToString(gradle.getBytes(StandardCharsets.UTF_8));
        server.stub("GET", OWNER_PATH + "gravit-aos/contents/app/build.gradle.kts", json(200, "{\"encoding\": \"base64\", \"content\": \"" + encoded + "\"}"));
    }

    public static void 비교_응답_등록(
            HttpStubServer server,
            String repo,
            String baseSha,
            String headSha,
            String filename
    ) {
        server.stub("GET", OWNER_PATH + repo + "/compare/" + baseSha + "..." + headSha, json(200, """
                {"status": "ahead", "ahead_by": 1, "behind_by": 0, "total_commits": 1,
                 "files": [{"filename": "%s", "status": "modified", "additions": 3, "deletions": 1, "changes": 4, "patch": "@@ -1 +1 @@"}]}
                """.formatted(filename)));
    }

    public static String 스프린트_페이지(
            List<String> targetPageIds,
            String figmaUrl
    ) {
        String relations = String.join(", ", targetPageIds.stream().map(id -> "{\"id\": \"" + id + "\"}").toList());
        String figma = figmaUrl == null ? "null" : "\"" + figmaUrl + "\"";
        return """
                {"object": "page", "id": "%s",
                 "parent": {"type": "database_id", "database_id": "%s"},
                 "properties": {
                   "이름": {"id": "title", "type": "title", "title": [{"plain_text": "%s"}]},
                   "기간": {"id": "p", "type": "date", "date": null},
                   "대상 기능": {"id": "rel", "type": "relation", "relation": [%s], "has_more": false},
                   "변경 요약": {"id": "s", "type": "rich_text", "rich_text": [{"plain_text": "유닛 상세 개편"}]},
                   "Figma QA 페이지": {"id": "f", "type": "url", "url": %s},
                   "QA 기준점": {"id": "b", "type": "rich_text", "rich_text": []}
                 }}
                """.formatted(SPRINT_PAGE_ID, SPRINT_DB, SPRINT_KEY, relations, figma);
    }

    public static String 다른_DB_페이지() {
        return 스프린트_페이지(List.of(), FIGMA_URL).replace(SPRINT_DB, SPEC_DB);
    }

    public static String 명세_페이지(
            String pageId,
            String screenId
    ) {
        return """
                {"object": "page", "id": "%s",
                 "parent": {"type": "database_id", "database_id": "%s"},
                 "properties": {
                   "화면 ID": {"id": "title", "type": "title", "title": [{"plain_text": "%s"}]},
                   "화면 이름": {"id": "n", "type": "rich_text", "rich_text": [{"plain_text": "%s 화면"}]},
                   "최종 검증일": {"id": "d", "type": "date", "date": null},
                   "스프린트": {"id": "PlQ-ZA", "type": "relation", "relation": [{"id": "%s"}], "has_more": false}
                 }}
                """.formatted(pageId, SPEC_DB, screenId, screenId, SPRINT_PAGE_ID);
    }

    public static String 명세_본문(
            String tableId,
            String text
    ) {
        return 본문_응답(표_블록(tableId), 문단("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", 글자(text)));
    }

    public static String 표_없는_명세_본문(String text) {
        return 본문_응답(문단("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", 글자(text)));
    }

    public static String 기본_정보_표(
            String specStatusLabel,
            List<String> partLabels,
            String route,
            List<String> apis
    ) {
        return 행_응답(
                헤더_행(),
                행("명세 상태", 글자(specStatusLabel)),
                행("파트", 글자(String.join(PART_DELIMITER, partLabels))),
                행("라우트", route == null ? 글자(EMPTY_VALUE) : 코드(route)),
                행("관련 API", apis.isEmpty() ? new ObjectNode[]{글자(EMPTY_VALUE)} : 목록(apis.toArray(String[]::new))),
                행("Figma", 링크("LRN-02", FIGMA_FRAME_URL)),
                행("FE 스펙 ID", 글자(EMPTY_VALUE))
        );
    }

    public static StubResponse LLM_응답(String content) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode()
                .put("id", "chatcmpl-test")
                .put("object", "chat.completion")
                .put("created", 1760000000)
                .put("model", "codex-terra");
        ObjectNode choice = root.putArray("choices").addObject()
                .put("index", 0)
                .put("finish_reason", "stop");
        choice.putObject("message")
                .put("role", "assistant")
                .put("content", content);
        root.putObject("usage")
                .put("prompt_tokens", 1)
                .put("completion_tokens", 1)
                .put("total_tokens", 2);
        return json(200, root.toString());
    }

    public static String 초안(ObjectNode... items) {
        ObjectNode output = OBJECT_MAPPER.createObjectNode();
        ArrayNode array = output.putArray("items");
        for (ObjectNode item : items) {
            array.add(item);
        }
        return output.toString();
    }

    public static ObjectNode 항목(
            String title,
            List<String> parts,
            boolean allMet,
            String personaKey,
            String testRef
    ) {
        ObjectNode item = OBJECT_MAPPER.createObjectNode()
                .put("evidence", "명세 주요 동작")
                .put("title", title)
                .put("expected", title + " 결과가 보인다");
        ArrayNode partArray = item.putArray("parts");
        parts.forEach(partArray::add);
        ObjectNode criteria = item.putObject("criteria");
        criteria.set("specDecidable", 기준(true));
        criteria.set("preconditionReproducible", 기준(true));
        criteria.set("observable", 기준(allMet));
        criteria.set("noExternalDependency", 기준(true));
        criteria.set("notSubjective", 기준(true));
        if (personaKey == null) {
            item.putNull("personaKey");
        } else {
            item.put("personaKey", personaKey);
        }
        if (testRef == null) {
            item.putNull("testRef");
        } else {
            item.put("testRef", testRef);
        }
        return item;
    }

    private static ObjectNode 기준(boolean met) {
        return OBJECT_MAPPER.createObjectNode()
                .put("reason", met ? "근거 있음" : "관찰 불가")
                .put("met", met);
    }

    private static String 브랜치(String sha) {
        return "{\"name\": \"release\", \"commit\": {\"sha\": \"" + sha + "\"}}";
    }
}
