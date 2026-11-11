package gravit.qa.sprint.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.support.HttpStubServer;
import gravit.qa.support.HttpStubServer.StubResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.글자;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.링크;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.목록;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.문단;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.본문_응답;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.제목3;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.코드;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.표_블록;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.표_행;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.행;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.행_응답;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.헤더_행;
import static gravit.qa.support.HttpStubServer.StubResponse.json;

public final class SetupStubFixture {

    public static final String SPRINT_PAGE_ID = "11111111111111111111111111111111";
    public static final String LRN_PAGE_ID = "22222222222222222222222222222222";
    public static final String LRN_TABLE_ID = "2222aaaa2222aaaa2222aaaa2222aaaa";
    public static final String SCREEN_HEADING_ID = "2222bbbb2222bbbb2222bbbb22220001";
    public static final String SCREEN_TABLE_ID = "2222bbbb2222bbbb2222bbbb22220002";
    public static final String C1_ROW_ID = "2222bbbb2222bbbb2222bbbb22220003";
    public static final String ACTION_HEADING_ID = "2222bbbb2222bbbb2222bbbb22220004";
    public static final String ACTION_TABLE_ID = "2222bbbb2222bbbb2222bbbb22220005";
    public static final String HISTORY_HEADING_ID = "2222bbbb2222bbbb2222bbbb22220006";
    public static final String HISTORY_TABLE_ID = "2222bbbb2222bbbb2222bbbb22220007";
    public static final String HISTORY_ROW_ID = "2222bbbb2222bbbb2222bbbb22220008";
    public static final String HISTORY_CHANGE = "유닛 카드에 진행률을 표시한다";
    public static final List<String> LRN_APIS = List.of("GET /api/v1/units/{unitId}");
    public static final String SPRINT_KEY = "3rd Agile";
    public static final String FILE_KEY = "AbC123";
    public static final String FIGMA_URL = "https://www.figma.com/design/" + FILE_KEY + "/Gravit?node-id=1-1";
    public static final String SERVER_SHA = "a".repeat(40);
    public static final String WEB_SHA = "b".repeat(40);
    public static final String AOS_SHA = "c".repeat(40);
    public static final String COMPLETIONS_PATH = "/v1/chat/completions";
    public static final String SPRINT_PATH = "/v1/pages/" + SPRINT_PAGE_ID;
    public static final String OPENAPI_PATH = "/v3/api-docs";
    public static final String SERVER_CONTROLLER = "src/main/java/gravit/code/unit/controller/UnitController.java";
    public static final String SERVER_SERVICE = "src/main/java/gravit/code/unit/service/UnitService.java";
    public static final String WEB_ROUTE = "apps/web/src/app/routes/_authenticated/_app-shell/learning.units.$unitId.index.tsx";
    public static final String WEB_PAGE = "apps/web/src/pages/unit/ui/UnitPage.tsx";

    private static final String SPRINT_DB = "4e533895-42c3-4a33-9a85-6820e77ccefc";
    private static final String SPEC_DB = "43fc286e-87ce-4ae3-862e-abf24d9096b3";
    private static final String OWNER_PATH = "/repos/Team-Gravit/";
    private static final String QA_BRANCH = "qa-baseline";
    private static final String FIGMA_FILE_PATH = "/v1/files/" + FILE_KEY;
    private static final String FIGMA_NODES_PATH = "/v1/files/" + FILE_KEY + "/nodes";
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
                  ]}
                ]},
                {"id": "5:0", "name": "AOS", "type": "SECTION", "children": [
                  {"id": "5:1", "name": "LEARNING", "type": "SECTION", "children": [
                    {"id": "5:2", "name": "LRN-02", "type": "FRAME", "absoluteBoundingBox": {"x": 0, "y": 0, "width": 360, "height": 740}}
                  ]}
                ]}
              ]}
            ]}}}}
            """;
    private static final String FRAME_NODES = """
            {"nodes": {
              "4:2": {"document": {"id": "4:2", "name": "LRN-02", "type": "FRAME", "absoluteBoundingBox": {"x": 0, "y": 0, "width": 1920, "height": 1080}, "children": [
                {"id": "4:10", "name": "유닛 카드", "type": "INSTANCE", "absoluteBoundingBox": {"x": 100, "y": 200, "width": 400, "height": 120}, "children": [
                  {"id": "4:11", "name": "제목", "type": "TEXT", "characters": "자료구조", "absoluteBoundingBox": {"x": 120, "y": 220, "width": 200, "height": 30}}
                ]}
              ]}},
              "4:5": {"document": {"id": "4:5", "name": "LRN-02/EMPTY", "type": "FRAME", "absoluteBoundingBox": {"x": 2000, "y": 0, "width": 1920, "height": 1080}, "children": []}},
              "5:2": {"document": {"id": "5:2", "name": "LRN-02", "type": "FRAME", "absoluteBoundingBox": {"x": 0, "y": 0, "width": 360, "height": 740}, "children": []}}
            }}
            """;
    private static final String FIGMA_FILE = """
            {"name": "Gravit", "document": {"id": "0:0", "type": "DOCUMENT", "children": [
              {"id": "1:1", "name": "QA", "type": "CANVAS"}
            ]}}
            """;
    private static final String API_DOCS = """
            {"openapi": "3.1.0", "paths": {"/api/v1/units/{unitId}": {"get": {"operationId": "getUnit", "responses": {"200": {"description": "ok"}}}}}}
            """;
    private static final String CONTROLLER_SOURCE = """
            package gravit.code.unit.controller;

            @RestController
            @RequestMapping("/api/v1/units")
            public class UnitController {

                private final UnitService unitService;

                @GetMapping("/{unitId}")
                public UnitResponse getUnit(@PathVariable Long unitId) {
                    return unitService.getUnit(unitId);
                }
            }
            """;
    private static final String SERVICE_SOURCE = """
            package gravit.code.unit.service;

            public class UnitService {
                public UnitResponse getUnit(Long unitId) {
                    return new UnitResponse(unitId, 40);
                }
            }
            """;
    private static final String ROUTE_SOURCE = """
            import { UnitPage } from '@/pages/unit/ui/UnitPage';

            export const Route = createFileRoute('/learning/units/$unitId/')({ component: UnitPage });
            """;
    private static final String PAGE_SOURCE = """
            export function UnitPage() {
              return <section><h1>유닛 목록</h1></section>;
            }
            """;

    private SetupStubFixture() {
    }

    public static void 외부_응답_등록(HttpStubServer server) {
        Notion_응답_등록(server, 스프린트_페이지(List.of(LRN_PAGE_ID), FIGMA_URL), true);
        Figma_응답_등록(server);
        GitHub_응답_등록(server);
        server.stub("GET", OPENAPI_PATH, json(200, API_DOCS));
    }

    public static void Notion_응답_등록(
            HttpStubServer server,
            String sprintPage,
            boolean withHistory
    ) {
        server.stub("GET", SPRINT_PATH, json(200, sprintPage));
        server.stub("GET", "/v1/pages/" + LRN_PAGE_ID, json(200, 명세_페이지(LRN_PAGE_ID, "LRN-02")));
        server.stub("GET", "/v1/blocks/" + LRN_PAGE_ID + "/children", json(200, 명세_본문(withHistory)));
        server.stub("GET", "/v1/blocks/" + LRN_TABLE_ID + "/children", json(200, 기본_정보_표("확정", List.of("서버", "웹"), "/learning/units/$unitId", LRN_APIS)));
        server.stub("GET", "/v1/blocks/" + SCREEN_TABLE_ID + "/children", json(200, 행_응답(
                표_행("2222bbbb2222bbbb2222bbbb22220010", "#", "요소", "내용", "언제", "근거"),
                표_행(C1_ROW_ID, "C1", "유닛 목록", "유닛 카드 목록", "항상", "🎨")
        )));
        server.stub("GET", "/v1/blocks/" + ACTION_TABLE_ID + "/children", json(200, 행_응답(
                표_행("2222bbbb2222bbbb2222bbbb22220011", "#", "이렇게 하면", "이렇게 됩니다", "근거"),
                표_행("2222bbbb2222bbbb2222bbbb22220012", "B1", "유닛 카드를 누르면", "유닛 상세로 갑니다", "💬")
        )));
        server.stub("GET", "/v1/blocks/" + HISTORY_TABLE_ID + "/children", json(200, 행_응답(
                표_행("2222bbbb2222bbbb2222bbbb22220013", "스프린트", "수정 사항", "반영일"),
                표_행(HISTORY_ROW_ID, SPRINT_KEY, HISTORY_CHANGE, "")
        )));
    }

    public static void Figma_응답_등록(HttpStubServer server) {
        server.stub("GET", FIGMA_FILE_PATH, json(200, FIGMA_FILE));
        server.stub("GET", FIGMA_NODES_PATH, json(200, QA_PAGE_NODES), json(200, QA_PAGE_NODES), json(200, FRAME_NODES));
    }

    public static void GitHub_응답_등록(HttpStubServer server) {
        server.stub("GET", OWNER_PATH + "gravit-server/branches/" + QA_BRANCH, json(200, 브랜치(SERVER_SHA)));
        server.stub("GET", OWNER_PATH + "gravit-web/branches/" + QA_BRANCH, json(200, 브랜치(WEB_SHA)));
        server.stub("GET", OWNER_PATH + "gravit-aos/branches/" + QA_BRANCH, json(200, 브랜치(AOS_SHA)));
        저장소_파일_등록(server, "gravit-server", SERVER_SHA, Map.of(SERVER_CONTROLLER, CONTROLLER_SOURCE, SERVER_SERVICE, SERVICE_SOURCE));
        저장소_파일_등록(server, "gravit-web", WEB_SHA, Map.of(WEB_ROUTE, ROUTE_SOURCE, WEB_PAGE, PAGE_SOURCE));
        저장소_파일_등록(server, "gravit-aos", AOS_SHA, Map.of());
    }

    public static void 저장소_파일_등록(
            HttpStubServer server,
            String repo,
            String commitSha,
            Map<String, String> pathToSource
    ) {
        ObjectNode tree = OBJECT_MAPPER.createObjectNode().put("sha", commitSha).put("truncated", false);
        ArrayNode entries = tree.putArray("tree");
        int index = 0;
        for (Map.Entry<String, String> entry : pathToSource.entrySet()) {
            String blobSha = String.format("%040x", (long) (repo.hashCode() & 0xffff) * 1000 + index++);
            entries.addObject()
                    .put("path", entry.getKey())
                    .put("type", "blob")
                    .put("sha", blobSha)
                    .put("size", entry.getValue().length());
            String encoded = Base64.getEncoder().encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8));
            server.stub("GET", OWNER_PATH + repo + "/git/blobs/" + blobSha, json(200, "{\"encoding\": \"base64\", \"content\": \"" + encoded + "\"}"));
        }
        server.stub("GET", OWNER_PATH + repo + "/git/trees/" + commitSha, json(200, tree.toString()));
    }

    public static void 브랜치_없음_등록(
            HttpStubServer server,
            String repo
    ) {
        server.stub("GET", OWNER_PATH + repo + "/branches/" + QA_BRANCH, json(404, "{\"message\": \"Branch not found\"}"));
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

    public static String 명세_본문(boolean withHistory) {
        List<ObjectNode> blocks = new ArrayList<>(List.of(
                표_블록(LRN_TABLE_ID),
                제목3(SCREEN_HEADING_ID, "화면에 보이는 것"),
                표_블록(SCREEN_TABLE_ID, 5),
                제목3(ACTION_HEADING_ID, "사용자가 할 수 있는 것"),
                표_블록(ACTION_TABLE_ID, 4)
        ));
        if (withHistory) {
            blocks.add(제목3(HISTORY_HEADING_ID, "수정 이력"));
            blocks.add(표_블록(HISTORY_TABLE_ID, 3));
        }
        return 본문_응답(blocks.toArray(ObjectNode[]::new));
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

    public static String 반영_출력() {
        return """
                {"evidence": "C1 내용에 진행률을 더한다",
                 "operations": [{"historyRow": 1, "op": "UPDATE_ROW", "target": "t2.r2", "blockType": null, "text": null,
                                 "cells": ["C1", "유닛 목록", "유닛 카드 목록과 진행률", "항상", "🎨"]}],
                 "skippedRows": []}
                """;
    }

    public static String 체크리스트_출력() {
        return """
                {"items": [
                  {"evidence": "C1 유닛 카드 목록, GET /api/v1/units/{unitId}", "specRowIds": ["C1"],
                   "title": "유닛 카드 목록과 진행률을 표시한다", "expected": "유닛 카드 목록과 진행률이 표시되는지 확인한다",
                   "parts": ["SERVER", "WEB"], "classificationReason": "응답 필드와 표시 문구는 코드로 판정할 수 있다", "classification": "AUTO"},
                  {"evidence": "B1 유닛 카드 클릭", "specRowIds": ["B1"],
                   "title": "유닛 카드를 누르면 유닛 상세로 이동한다", "expected": "카드를 누르면 유닛 상세 화면으로 가는지 확인한다",
                   "parts": ["WEB"], "classificationReason": "실제 화면 이동을 확인해야 한다", "classification": "MANUAL"}
                ]}
                """;
    }

    public static String 디자인_출력() {
        return """
                {"items": [
                  {"platform": "WEB", "evidence": "유닛 카드 레이어가 있다", "specRowIds": ["C1"],
                   "title": "유닛 목록 영역 시안이 명세와 같다", "expected": "유닛 카드 목록이 시안에 있는지 확인한다",
                   "figmaNodeIds": ["4:10"], "reason": "유닛 카드 컴포넌트가 있다", "verdict": "PASS"}
                ]}
                """;
    }

    public static String 서버_판정_출력(long itemId) {
        return """
                {"results": [{"itemId": %d, "evidence": [{"path": "gravit-server/%s", "startLine": 9, "endLine": 12}],
                              "reason": "유닛 조회 API가 진행률을 돌려준다", "verdict": "PASS"}]}
                """.formatted(itemId, SERVER_CONTROLLER);
    }

    public static String 웹_판정_출력(long itemId) {
        return """
                {"results": [{"itemId": %d, "evidence": [{"path": "gravit-web/%s", "startLine": 1, "endLine": 3}],
                              "reason": "진행률을 표시하는 코드가 없다", "verdict": "FAIL"}]}
                """.formatted(itemId, WEB_PAGE);
    }

    private static String 브랜치(String sha) {
        return "{\"name\": \"" + QA_BRANCH + "\", \"commit\": {\"sha\": \"" + sha + "\"}}";
    }
}
