package gravit.qa.adapter.figma.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.figma.config.FigmaProps;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameImagesDto;
import gravit.qa.adapter.figma.dto.internal.FigmaQaFramesDto;
import gravit.qa.adapter.figma.support.FigmaFileKeys;
import gravit.qa.adapter.figma.support.FigmaFrameParser;
import gravit.qa.adapter.support.ExternalCallExecutor;
import gravit.qa.adapter.support.ExternalResponses;
import gravit.qa.adapter.support.HttpRequestFactories;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static gravit.qa.adapter.domain.ExternalApi.FIGMA;
import static gravit.qa.adapter.domain.ExternalApi.FIGMA_IMAGE;

@Component
public class FigmaClient {

    private static final String TOKEN_HEADER = "X-Figma-Token";
    private static final String FILE_PATH = "/v1/files/{fileKey}";
    private static final String NODES_PATH = "/v1/files/{fileKey}/nodes";
    private static final String IMAGES_PATH = "/v1/images/{fileKey}";
    private static final String DEPTH_PARAM = "depth";
    private static final String IDS_PARAM = "ids";
    private static final String FORMAT_PARAM = "format";
    private static final String SCALE_PARAM = "scale";
    private static final String PNG_FORMAT = "png";
    private static final String CANVAS = "CANVAS";
    private static final String ID_DELIMITER = ",";
    private static final String DOWNLOAD_OPERATION = "PNG 다운로드";
    private static final int PAGES_DEPTH = 1;
    private static final int QA_PAGE_DEPTH = 4;
    private static final int SECTION_EXPAND_DEPTH = 2;
    private static final int MAX_EXPAND_ROUNDS = 5;
    private static final int EXPORT_BATCH_SIZE = 50;
    private static final int EXPORT_SCALE = 1;

    private final RestClient restClient;
    private final RestClient downloadClient;

    private final ExternalCallExecutor externalCallExecutor;

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public FigmaClient(
            RestClient.Builder restClientBuilder,
            ExternalCallExecutor externalCallExecutor,
            Clock clock,
            ObjectMapper objectMapper,
            FigmaProps figmaProps
    ) {
        this.downloadClient = restClientBuilder.clone()
                .requestFactory(HttpRequestFactories.jdk(figmaProps.timeout()))
                .build();
        this.restClient = restClientBuilder
                .baseUrl(figmaProps.baseUrl())
                .requestFactory(HttpRequestFactories.jdk(figmaProps.timeout()))
                .defaultHeaders(headers -> {
                    if (StringUtils.hasText(figmaProps.token())) {
                        headers.set(TOKEN_HEADER, figmaProps.token());
                    }
                })
                .build();
        this.externalCallExecutor = externalCallExecutor;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public FigmaQaFramesDto getQaFrames(
            String fileKey,
            String sprintKey
    ) {
        FigmaFileKeys.requireValid(fileKey);
        JsonNode file = getJson("GET /v1/files/" + fileKey + "?depth=" + PAGES_DEPTH, uri -> uri.path(FILE_PATH)
                .queryParam(DEPTH_PARAM, PAGES_DEPTH)
                .build(fileKey));

        Optional<JsonNode> qaPage = findQaPage(file);
        if (qaPage.isEmpty()) {
            return FigmaFrameParser.qaPageNotFound(fileKey);
        }

        String qaPageId = qaPage.get().path("id").asText();
        JsonNode qaPageNode = getNodes(fileKey, List.of(qaPageId), QA_PAGE_DEPTH).get(qaPageId);
        if (qaPageNode == null) {
            return FigmaFrameParser.qaPageNotFound(fileKey);
        }

        expandTruncatedSections(fileKey, qaPageNode, sprintKey);
        return FigmaFrameParser.parse(fileKey, qaPageNode, sprintKey);
    }

    public FigmaFrameImagesDto exportPng(
            String fileKey,
            List<String> nodeIds
    ) {
        FigmaFileKeys.requireValid(fileKey);
        List<String> targets = nodeIds.stream().distinct().toList();

        Map<String, byte[]> nodeIdToPng = new LinkedHashMap<>();
        List<String> failedNodeIds = new ArrayList<>();
        for (int start = 0; start < targets.size(); start += EXPORT_BATCH_SIZE) {
            List<String> batch = targets.subList(start, Math.min(targets.size(), start + EXPORT_BATCH_SIZE));
            String operation = "GET /v1/images/" + fileKey;
            JsonNode body = getJson(operation, uri -> uri.path(IMAGES_PATH)
                    .queryParam(IDS_PARAM, String.join(ID_DELIMITER, batch))
                    .queryParam(FORMAT_PARAM, PNG_FORMAT)
                    .queryParam(SCALE_PARAM, EXPORT_SCALE)
                    .build(fileKey));

            String error = body.path("err").asText(null);
            if (StringUtils.hasText(error)) {
                throw new NonRetryableExternalApiException(FIGMA, null, "Figma 이미지 렌더 요청이 실패했습니다: " + error);
            }

            for (String nodeId : batch) {
                String imageUrl = body.path("images").path(nodeId).asText(null);
                if (StringUtils.hasText(imageUrl)) {
                    nodeIdToPng.put(nodeId, download(imageUrl));
                } else {
                    failedNodeIds.add(nodeId);
                }
            }
        }
        return new FigmaFrameImagesDto(nodeIdToPng, failedNodeIds);
    }

    private Optional<JsonNode> findQaPage(JsonNode file) {
        for (JsonNode page : file.path("document").path("children")) {
            if (CANVAS.equals(page.path("type").asText()) && FigmaFrameParser.QA_PAGE_NAME.equals(page.path("name").asText().trim())) {
                return Optional.of(page);
            }
        }
        return Optional.empty();
    }

    private void expandTruncatedSections(
            String fileKey,
            JsonNode qaPageNode,
            String sprintKey
    ) {
        for (int round = 0; round < MAX_EXPAND_ROUNDS; round++) {
            List<ObjectNode> truncated = FigmaFrameParser.findTruncatedSections(qaPageNode, sprintKey);
            if (truncated.isEmpty()) {
                return;
            }

            List<String> sectionIds = truncated.stream().map(section -> section.path("id").asText()).toList();
            Map<String, JsonNode> idToNode = getNodes(fileKey, sectionIds, SECTION_EXPAND_DEPTH);
            for (ObjectNode section : truncated) {
                JsonNode expanded = idToNode.get(section.path("id").asText());
                section.set("children", expanded == null ? objectMapper.createArrayNode() : expanded.path("children"));
            }
        }
    }

    private Map<String, JsonNode> getNodes(
            String fileKey,
            List<String> nodeIds,
            int depth
    ) {
        String ids = String.join(ID_DELIMITER, nodeIds);
        JsonNode body = getJson("GET /v1/files/" + fileKey + "/nodes?depth=" + depth, uri -> uri.path(NODES_PATH)
                .queryParam(IDS_PARAM, ids)
                .queryParam(DEPTH_PARAM, depth)
                .build(fileKey));

        Map<String, JsonNode> idToNode = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            JsonNode document = body.path("nodes").path(nodeId).path("document");
            if (document.isObject()) {
                idToNode.put(nodeId, document);
            }
        }
        return idToNode;
    }

    private byte[] download(String imageUrl) {
        URI uri = URI.create(imageUrl);
        return externalCallExecutor.execute(FIGMA_IMAGE, DOWNLOAD_OPERATION, () -> downloadClient.get()
                .uri(uri)
                .exchange((request, response) -> ExternalResponses.readBytes(FIGMA_IMAGE, DOWNLOAD_OPERATION, response, clock)));
    }

    private JsonNode getJson(
            String operation,
            Function<UriBuilder, URI> uriFunction
    ) {
        String body = externalCallExecutor.execute(FIGMA, operation, () -> restClient.get()
                .uri(uriFunction)
                .exchange((request, response) -> ExternalResponses.readText(FIGMA, operation, response, clock)));
        return ExternalResponses.parseJson(objectMapper, FIGMA, operation, body);
    }
}
