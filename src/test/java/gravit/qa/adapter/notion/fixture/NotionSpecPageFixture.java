package gravit.qa.adapter.notion.fixture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;

import java.util.ArrayList;
import java.util.List;

public final class NotionSpecPageFixture {

    public static final String QUIZ_02_PAGE_ID = "3fb5b32f003c812f99f0cadcbd5525cc";
    public static final String QUIZ_02_TABLE_ID = "3fb5b32f003c812f0000000000000001";
    public static final String MY_04_PAGE_ID = "3fb5b32f003c813b96ccd49360d389e5";
    public static final String MY_04_TABLE_ID = "3fb5b32f003c813b0000000000000001";
    public static final String ERR_01_PAGE_ID = "3fb5b32f003c8195bc18feb44c758dc8";
    public static final String ERR_01_TABLE_ID = "3fb5b32f003c81950000000000000001";
    public static final String AI_01_PAGE_ID = "3fb5b32f003c81e5b4d9e4550cb24a3f";
    public static final String AI_01_TABLE_ID = "3fb5b32f003c81e50000000000000001";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FIGMA_FRAME_URL = "https://www.figma.com/design/hu4c6qCEMB62qHXk2v8Gsl/?node-id=";
    private static final String LIST_MARKER = "  • ";
    private static final String LINE_BREAK = "\n";

    private NotionSpecPageFixture() {
    }

    public static String QUIZ_02_본문() {
        return 본문_응답(
                표_블록(QUIZ_02_TABLE_ID),
                콜아웃("3fb5b32f003c812f0000000000000002",
                        글자("문제를 처음 열었을 때, 아직 답하지 않은 상태의 화면입니다.\n\n상태: "), 코드("객관식"), 글자(" "), 코드("주관식")),
                제목3("3fb5b32f003c812f0000000000000003", "화면에 보이는 것"),
                제목3("3fb5b32f003c812f0000000000000004", "사용자가 할 수 있는 것"),
                제목3("3fb5b32f003c812f0000000000000005", "예외 상황")
        );
    }

    public static String QUIZ_02_기본_정보_표() {
        return 행_응답(
                헤더_행(),
                행("명세 상태", 글자("확정")),
                행("파트", 글자("서버, 웹, 앱")),
                행("라우트", 코드("/learning/lessons/$lessonId")),
                행("관련 API", 목록(
                        "GET /api/v1/problems/{lessonId}",
                        "POST /api/v1/problems/results"
                )),
                행("Figma", 링크("QUIZ-02", FIGMA_FRAME_URL + "15432-32878")),
                행("FE 스펙 ID", 글자("-"))
        );
    }

    public static String MY_04_본문() {
        return 본문_응답(
                표_블록(MY_04_TABLE_ID),
                제목3("3fb5b32f003c813b0000000000000002", "화면에 보이는 것")
        );
    }

    public static String MY_04_기본_정보_표() {
        return 행_응답(
                헤더_행(),
                행("명세 상태", 글자("확정")),
                행("파트", 글자("서버, 웹, 앱")),
                행("라우트", 코드("/my/social")),
                행("관련 API", 목록(
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
                )),
                행("Figma", 링크("MY-04", FIGMA_FRAME_URL + "15432-37414")),
                행("FE 스펙 ID", 글자("-"))
        );
    }

    public static String ERR_01_본문() {
        return 본문_응답(
                표_블록(ERR_01_TABLE_ID),
                제목3("3fb5b32f003c81950000000000000002", "예외 상황"),
                문단("3fb5b32f003c81950000000000000003", 글자("없음"))
        );
    }

    public static String ERR_01_기본_정보_표() {
        return 행_응답(
                헤더_행(),
                행("명세 상태", 글자("확정")),
                행("파트", 글자("웹, 앱")),
                행("라우트", 글자("-")),
                행("관련 API", 글자("-")),
                행("Figma", 링크("ERR-01", FIGMA_FRAME_URL + "16077-29655")),
                행("FE 스펙 ID", 글자("E.1"))
        );
    }

    public static String AI_01_본문() {
        return 본문_응답(
                표_블록(AI_01_TABLE_ID),
                제목3("3fb5b32f003c81e50000000000000002", "목적"),
                목록_블록("3fb5b32f003c81e50000000000000003", "면접 답변을 음성으로 할지 텍스트로 할지 고른다")
        );
    }

    public static String AI_01_기본_정보_표() {
        return 행_응답(
                행(글자("항목"), 글자("값")),
                행(글자("명세 상태"), 글자("확인 필요")),
                행(글자("파트"), 글자("웹, 앱")),
                행(글자("라우트"), 글자("-")),
                행(글자("관련 API"), 글자("-")),
                행(글자("Figma"), 링크("AI-01", FIGMA_FRAME_URL + "15432-38774")),
                행(글자("FE 스펙 ID"), 글자("-"))
        );
    }

    public static List<NotionBlockDto> 블록_트리(
            String pageChildren,
            String tableRows
    ) {
        List<NotionBlockDto> rows = new ArrayList<>();
        읽기(tableRows).path("results").forEach(row -> rows.add(new NotionBlockDto(row, List.of())));

        List<NotionBlockDto> blocks = new ArrayList<>();
        읽기(pageChildren).path("results").forEach(block -> blocks.add(new NotionBlockDto(
                block,
                blocks.isEmpty() && "table".equals(block.path("type").asText()) ? rows : List.of()
        )));
        return blocks;
    }

    public static String 본문_응답(ObjectNode... blocks) {
        return 목록_응답(blocks);
    }

    public static String 행_응답(ObjectNode... rows) {
        return 목록_응답(rows);
    }

    public static ObjectNode 헤더_행() {
        return 행(굵게("항목"), 글자("값"));
    }

    public static ObjectNode 행(
            String item,
            ObjectNode... value
    ) {
        return 행(굵게(item), value);
    }

    public static ObjectNode 행(
            ObjectNode item,
            ObjectNode... value
    ) {
        ObjectNode row = 블록(null, "table_row", false);
        ArrayNode cells = row.putObject("table_row").putArray("cells");
        cells.addArray().add(item);
        ArrayNode valueCell = cells.addArray();
        for (ObjectNode text : value) {
            valueCell.add(text);
        }
        return row;
    }

    public static ObjectNode 표_블록(String id) {
        ObjectNode block = 블록(id, "table", true);
        block.putObject("table")
                .put("table_width", 2)
                .put("has_column_header", true)
                .put("has_row_header", false);
        return block;
    }

    public static ObjectNode 문단(
            String id,
            ObjectNode... texts
    ) {
        return 텍스트_블록(id, "paragraph", texts);
    }

    public static ObjectNode 제목3(
            String id,
            String text
    ) {
        return 텍스트_블록(id, "heading_3", 글자(text));
    }

    public static ObjectNode 콜아웃(
            String id,
            ObjectNode... texts
    ) {
        return 텍스트_블록(id, "callout", texts);
    }

    public static ObjectNode 목록_블록(
            String id,
            String text
    ) {
        return 텍스트_블록(id, "bulleted_list_item", 글자(text));
    }

    public static ObjectNode[] 목록(String... lines) {
        List<ObjectNode> texts = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            texts.add(글자((index == 0 ? "" : LINE_BREAK) + LIST_MARKER));
            texts.add(코드(lines[index]));
        }
        return texts.toArray(ObjectNode[]::new);
    }

    public static ObjectNode 글자(String content) {
        return 서식_글자(content, false, false, null);
    }

    public static ObjectNode 굵게(String content) {
        return 서식_글자(content, true, false, null);
    }

    public static ObjectNode 코드(String content) {
        return 서식_글자(content, false, true, null);
    }

    public static ObjectNode 링크(
            String content,
            String url
    ) {
        return 서식_글자(content, false, false, url);
    }

    private static ObjectNode 서식_글자(
            String content,
            boolean bold,
            boolean code,
            String url
    ) {
        ObjectNode text = OBJECT_MAPPER.createObjectNode().put("type", "text");
        ObjectNode body = text.putObject("text").put("content", content);
        if (url == null) {
            body.putNull("link");
        } else {
            body.putObject("link").put("url", url);
        }
        text.putObject("annotations")
                .put("bold", bold)
                .put("italic", false)
                .put("strikethrough", false)
                .put("underline", false)
                .put("code", code)
                .put("color", "default");
        text.put("plain_text", content);
        if (url == null) {
            text.putNull("href");
        } else {
            text.put("href", url);
        }
        return text;
    }

    private static ObjectNode 텍스트_블록(
            String id,
            String type,
            ObjectNode... texts
    ) {
        ObjectNode block = 블록(id, type, false);
        ArrayNode richText = block.putObject(type).putArray("rich_text");
        for (ObjectNode text : texts) {
            richText.add(text);
        }
        return block;
    }

    private static ObjectNode 블록(
            String id,
            String type,
            boolean hasChildren
    ) {
        ObjectNode block = OBJECT_MAPPER.createObjectNode().put("object", "block");
        if (id == null) {
            block.putNull("id");
        } else {
            block.put("id", id);
        }
        return block.put("type", type).put("has_children", hasChildren);
    }

    private static String 목록_응답(ObjectNode... nodes) {
        ObjectNode response = OBJECT_MAPPER.createObjectNode().put("object", "list");
        ArrayNode results = response.putArray("results");
        for (ObjectNode node : nodes) {
            results.add(node);
        }
        return response.put("has_more", false).putNull("next_cursor").toString();
    }

    private static JsonNode 읽기(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
