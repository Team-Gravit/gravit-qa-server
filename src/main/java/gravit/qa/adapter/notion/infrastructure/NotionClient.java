package gravit.qa.adapter.notion.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.notion.config.NotionProps;
import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;
import gravit.qa.adapter.notion.dto.internal.NotionPageBodyDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecDto;
import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.adapter.notion.support.NotionBlockRenderer;
import gravit.qa.adapter.notion.support.NotionIds;
import gravit.qa.adapter.notion.support.NotionPropertyReader;
import gravit.qa.adapter.notion.support.NotionWriteGuard;
import gravit.qa.adapter.support.ExternalCallExecutor;
import gravit.qa.adapter.support.ExternalResponses;
import gravit.qa.adapter.support.HttpRequestFactories;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static gravit.qa.adapter.domain.ExternalApi.NOTION;
import static gravit.qa.adapter.notion.support.NotionWriteGuard.BASELINE_PROPERTY;

@Component
public class NotionClient {

    public static final String SPRINT_NAME = "이름";
    public static final String SPRINT_PERIOD = "기간";
    public static final String SPRINT_TARGET_FEATURES = "대상 기능";
    public static final String SPRINT_CHANGE_SUMMARY = "변경 요약";
    public static final String SPRINT_FIGMA_QA_PAGE = "Figma QA 페이지";
    public static final String SPEC_SCREEN_ID = "화면 ID";
    public static final String SPEC_NAME = "이름";
    public static final String SPEC_PARTS = "파트";
    public static final String SPEC_ROUTE = "라우트";
    public static final String SPEC_RELATED_API = "관련 API";
    public static final String SPEC_FIGMA = "Figma";
    public static final String SPEC_STATUS = "명세 상태";
    public static final String SPEC_LAST_VERIFIED_AT = "최종 검증일";

    private static final String VERSION_HEADER = "Notion-Version";
    private static final String PAGE_PATH = "/v1/pages/";
    private static final String BLOCK_CHILDREN_PATH = "/v1/blocks/%s/children";
    private static final String DATABASE_QUERY_PATH = "/v1/databases/%s/query";
    private static final String PROPERTY_ITEM_PATH = "/v1/pages/%s/properties/%s";
    private static final String PAGE_SIZE_QUERY = "page_size=";
    private static final String START_CURSOR_QUERY = "&start_cursor=";
    private static final String DATABASE_PARENT_TYPE = "database_id";
    private static final Set<String> UNEXPANDED_BLOCK_TYPES = Set.of("child_page", "child_database");
    private static final int PAGE_SIZE = 100;
    private static final int MAX_BLOCK_DEPTH = 8;
    private static final int MAX_RICH_TEXT_LENGTH = 2000;

    private final RestClient restClient;

    private final ExternalCallExecutor externalCallExecutor;

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final NotionProps notionProps;

    public NotionClient(
            RestClient.Builder restClientBuilder,
            ExternalCallExecutor externalCallExecutor,
            Clock clock,
            ObjectMapper objectMapper,
            NotionProps notionProps
    ) {
        this.restClient = restClientBuilder
                .requestFactory(HttpRequestFactories.jdk(notionProps.timeout()))
                .defaultHeaders(headers -> {
                    headers.set(VERSION_HEADER, notionProps.version());
                    if (StringUtils.hasText(notionProps.token())) {
                        headers.setBearerAuth(notionProps.token());
                    }
                })
                .build();
        this.externalCallExecutor = externalCallExecutor;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.notionProps = notionProps;
    }

    public NotionSprintDto getSprint(String pageIdOrUrl) {
        String pageId = NotionIds.normalize(pageIdOrUrl);
        JsonNode page = request(HttpMethod.GET, PAGE_PATH + pageId, null, null);

        requireParent(page, notionProps.sprintDatabaseId(), "스프린트");
        return toSprint(page);
    }

    public List<NotionSprintDto> findRecentSprints(int limit) {
        ObjectNode body = objectMapper.createObjectNode().put("page_size", Math.min(limit, PAGE_SIZE));
        body.putArray("sorts").addObject()
                .put("timestamp", "created_time")
                .put("direction", "descending");

        String path = DATABASE_QUERY_PATH.formatted(NotionIds.normalize(notionProps.sprintDatabaseId()));
        JsonNode response = request(HttpMethod.POST, path, null, body);

        List<NotionSprintDto> sprints = new ArrayList<>();
        response.path("results").forEach(page -> sprints.add(toSprint(page)));
        return sprints;
    }

    public NotionSpecDto getSpec(String pageIdOrUrl) {
        String pageId = NotionIds.normalize(pageIdOrUrl);
        JsonNode page = request(HttpMethod.GET, PAGE_PATH + pageId, null, null);

        requireParent(page, notionProps.specDatabaseId(), "기능 명세");
        return toSpec(page);
    }

    public List<NotionSpecDto> getTargetSpecs(NotionSprintDto sprint) {
        return sprint.targetFeaturePageIds().stream()
                .map(this::getSpec)
                .toList();
    }

    public NotionPageBodyDto getPageBody(String pageIdOrUrl) {
        String pageId = NotionIds.normalize(pageIdOrUrl);
        List<NotionBlockDto> blocks = fetchChildren(pageId, 0);

        return new NotionPageBodyDto(
                pageId,
                blocks,
                NotionBlockRenderer.toMarkdown(blocks),
                toJsonArray(blocks).toString()
        );
    }

    public void updateSprintBaseline(
            String sprintPageIdOrUrl,
            String baseline
    ) {
        NotionSprintDto sprint = getSprint(sprintPageIdOrUrl);

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode richText = body.putObject("properties").putObject(BASELINE_PROPERTY).putArray("rich_text");
        String text = baseline == null ? "" : baseline;
        for (int start = 0; start < text.length(); start += MAX_RICH_TEXT_LENGTH) {
            richText.addObject()
                    .put("type", "text")
                    .putObject("text").put("content", text.substring(start, Math.min(text.length(), start + MAX_RICH_TEXT_LENGTH)));
        }

        request(HttpMethod.PATCH, PAGE_PATH + sprint.pageId(), null, body);
    }

    private NotionSprintDto toSprint(JsonNode page) {
        String pageId = NotionIds.normalize(page.path("id").asText());
        JsonNode properties = page.path("properties");

        String name = NotionPropertyReader.text(properties.path(SPRINT_NAME));
        if (!StringUtils.hasText(name)) {
            throw ExternalResponses.invalidResponse(NOTION, "GET " + PAGE_PATH + pageId, "스프린트 '" + SPRINT_NAME + "' 값이 없습니다");
        }

        return new NotionSprintDto(
                pageId,
                name.trim(),
                NotionPropertyReader.dateStart(properties.path(SPRINT_PERIOD)),
                NotionPropertyReader.dateEnd(properties.path(SPRINT_PERIOD)),
                relationIds(pageId, properties.path(SPRINT_TARGET_FEATURES)),
                NotionPropertyReader.text(properties.path(SPRINT_CHANGE_SUMMARY)),
                NotionPropertyReader.url(properties.path(SPRINT_FIGMA_QA_PAGE)),
                NotionPropertyReader.text(properties.path(BASELINE_PROPERTY)),
                page.toString()
        );
    }

    private NotionSpecDto toSpec(JsonNode page) {
        String pageId = NotionIds.normalize(page.path("id").asText());
        JsonNode properties = page.path("properties");

        String screenId = NotionPropertyReader.text(properties.path(SPEC_SCREEN_ID));
        if (!StringUtils.hasText(screenId)) {
            throw ExternalResponses.invalidResponse(NOTION, "GET " + PAGE_PATH + pageId, "기능 명세 '" + SPEC_SCREEN_ID + "' 값이 없습니다");
        }

        return new NotionSpecDto(
                pageId,
                screenId.trim(),
                NotionPropertyReader.text(properties.path(SPEC_NAME)),
                NotionPropertyReader.multiSelect(properties.path(SPEC_PARTS)),
                NotionPropertyReader.text(properties.path(SPEC_ROUTE)),
                NotionPropertyReader.text(properties.path(SPEC_RELATED_API)),
                NotionPropertyReader.url(properties.path(SPEC_FIGMA)),
                NotionPropertyReader.select(properties.path(SPEC_STATUS)),
                NotionPropertyReader.dateStart(properties.path(SPEC_LAST_VERIFIED_AT)),
                page.toString()
        );
    }

    private List<String> relationIds(
            String pageId,
            JsonNode relationProperty
    ) {
        if (!NotionPropertyReader.hasMore(relationProperty)) {
            return NotionPropertyReader.relationIds(relationProperty);
        }

        String path = PROPERTY_ITEM_PATH.formatted(pageId, relationProperty.path("id").asText());
        List<String> ids = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode response = request(HttpMethod.GET, path, pageQuery(cursor), null);
            response.path("results").forEach(item -> ids.add(NotionIds.normalize(item.path("relation").path("id").asText())));
            cursor = nextCursor(response);
        } while (cursor != null);
        return ids;
    }

    private List<NotionBlockDto> fetchChildren(
            String blockId,
            int depth
    ) {
        String path = BLOCK_CHILDREN_PATH.formatted(NotionIds.normalize(blockId));
        List<JsonNode> blocks = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode response = request(HttpMethod.GET, path, pageQuery(cursor), null);
            response.path("results").forEach(blocks::add);
            cursor = nextCursor(response);
        } while (cursor != null);

        List<NotionBlockDto> results = new ArrayList<>();
        for (JsonNode block : blocks) {
            List<NotionBlockDto> children = shouldExpand(block, depth)
                    ? fetchChildren(block.path("id").asText(), depth + 1)
                    : List.of();
            results.add(new NotionBlockDto(block, children));
        }
        return results;
    }

    private boolean shouldExpand(
            JsonNode block,
            int depth
    ) {
        return block.path("has_children").asBoolean(false)
                && !UNEXPANDED_BLOCK_TYPES.contains(block.path("type").asText())
                && depth < MAX_BLOCK_DEPTH;
    }

    private ArrayNode toJsonArray(List<NotionBlockDto> blocks) {
        ArrayNode array = objectMapper.createArrayNode();
        for (NotionBlockDto block : blocks) {
            ObjectNode copy = block.block().deepCopy();
            copy.set("children", toJsonArray(block.children()));
            array.add(copy);
        }
        return array;
    }

    private void requireParent(
            JsonNode page,
            String databaseId,
            String databaseName
    ) {
        JsonNode parent = page.path("parent");
        if (!DATABASE_PARENT_TYPE.equals(parent.path("type").asText())
                || !NotionIds.isSame(parent.path(DATABASE_PARENT_TYPE).asText(), databaseId)) {
            throw new NonRetryableExternalApiException(NOTION, null, "Notion 페이지가 " + databaseName + " DB의 행이 아닙니다. pageId=" + page.path("id").asText());
        }
    }

    private JsonNode request(
            HttpMethod method,
            String path,
            String query,
            JsonNode body
    ) {
        NotionWriteGuard.check(method, path, body);

        String operation = method.name() + " " + path;
        URI uri = URI.create(notionProps.baseUrl() + path + (query == null ? "" : "?" + query));
        String responseBody = externalCallExecutor.execute(NOTION, operation, () -> {
            RestClient.RequestBodySpec spec = restClient.method(method).uri(uri);
            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON).body(body.toString());
            }
            return spec.exchange((request, response) -> ExternalResponses.readText(NOTION, operation, response, clock));
        });
        return ExternalResponses.parseJson(objectMapper, NOTION, operation, responseBody);
    }

    private String pageQuery(String cursor) {
        return PAGE_SIZE_QUERY + PAGE_SIZE + (cursor == null ? "" : START_CURSOR_QUERY + cursor);
    }

    private String nextCursor(JsonNode response) {
        return response.path("has_more").asBoolean(false) ? response.path("next_cursor").asText(null) : null;
    }
}
