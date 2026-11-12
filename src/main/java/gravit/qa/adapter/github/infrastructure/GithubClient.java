package gravit.qa.adapter.github.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.exception.BranchNotFoundException;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.github.config.GithubProps;
import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.adapter.github.dto.internal.GithubTreeDto;
import gravit.qa.adapter.github.dto.internal.GithubTreeEntryDto;
import gravit.qa.adapter.support.ExternalCallExecutor;
import gravit.qa.adapter.support.ExternalResponses;
import gravit.qa.adapter.support.HttpRequestFactories;
import gravit.qa.adapter.support.RetryAfterParser;
import gravit.qa.adapter.support.RetryableResponseException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.util.UriBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import static gravit.qa.adapter.domain.ExternalApi.GITHUB;

@Component
public class GithubClient {

    private static final String ACCEPT = "application/vnd.github+json";
    private static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    private static final String API_VERSION = "2022-11-28";
    private static final String RATE_LIMIT_REMAINING_HEADER = "x-ratelimit-remaining";
    private static final String RATE_LIMIT_RESET_HEADER = "x-ratelimit-reset";
    private static final String RATE_LIMIT_EXHAUSTED = "0";
    private static final String REPO_PATH = "/repos/{owner}/{repo}";
    private static final String BRANCH_PATH = REPO_PATH + "/branches/{branch}";
    private static final String TREE_PATH = REPO_PATH + "/git/trees/{sha}";
    private static final String BLOB_PATH = REPO_PATH + "/git/blobs/{sha}";
    private static final String RECURSIVE_PARAM = "recursive";
    private static final String RECURSIVE_VALUE = "1";
    private static final String BLOB_TYPE = "blob";
    private static final String BASE64_ENCODING = "base64";
    private static final int STATUS_FORBIDDEN = 403;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_TOO_MANY_REQUESTS = 429;

    private final RestClient restClient;

    private final ExternalCallExecutor externalCallExecutor;

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final GithubProps githubProps;

    public GithubClient(
            RestClient.Builder restClientBuilder,
            ExternalCallExecutor externalCallExecutor,
            Clock clock,
            ObjectMapper objectMapper,
            GithubProps githubProps
    ) {
        this.restClient = restClientBuilder
                .baseUrl(githubProps.baseUrl())
                .requestFactory(HttpRequestFactories.jdk(githubProps.timeout()))
                .defaultHeaders(headers -> {
                    headers.set(HttpHeaders.ACCEPT, ACCEPT);
                    headers.set(API_VERSION_HEADER, API_VERSION);
                    if (StringUtils.hasText(githubProps.token())) {
                        headers.setBearerAuth(githubProps.token());
                    }
                })
                .build();
        this.externalCallExecutor = externalCallExecutor;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.githubProps = githubProps;
    }

    public String getBranchHeadSha(
            GithubRepo repo,
            String branch
    ) {
        String operation = "GET " + repoPath(repo) + "/branches/" + branch;
        JsonNode body;
        try {
            body = getJson(operation, uri -> uri.path(BRANCH_PATH).build(githubProps.owner(), repo.getRepoName(), branch));
        } catch (NonRetryableExternalApiException e) {
            if (Integer.valueOf(STATUS_NOT_FOUND).equals(e.getStatus())) {
                throw new BranchNotFoundException(repo, branch);
            }
            throw e;
        }
        return ExternalResponses.requireText(body, "/commit/sha", GITHUB, operation);
    }

    public GithubTreeDto getTree(
            GithubRepo repo,
            String commitSha
    ) {
        String operation = "GET " + repoPath(repo) + "/git/trees/" + commitSha + "?recursive=1";
        JsonNode body = getJson(operation, uri -> uri.path(TREE_PATH)
                .queryParam(RECURSIVE_PARAM, RECURSIVE_VALUE)
                .build(githubProps.owner(), repo.getRepoName(), commitSha));

        List<GithubTreeEntryDto> entries = new ArrayList<>();
        for (JsonNode entry : body.path("tree")) {
            if (BLOB_TYPE.equals(entry.path("type").asText())) {
                entries.add(new GithubTreeEntryDto(entry.path("path").asText(), entry.path("sha").asText(), entry.path("size").asLong()));
            }
        }
        return new GithubTreeDto(commitSha, body.path("truncated").asBoolean(false), entries);
    }

    public String getBlob(
            GithubRepo repo,
            String blobSha
    ) {
        String operation = "GET " + repoPath(repo) + "/git/blobs/" + blobSha;
        JsonNode body = getJson(operation, uri -> uri.path(BLOB_PATH).build(githubProps.owner(), repo.getRepoName(), blobSha));

        if (!BASE64_ENCODING.equals(body.path("encoding").asText())) {
            throw ExternalResponses.invalidResponse(GITHUB, operation, "base64가 아닌 인코딩(" + body.path("encoding").asText() + ")입니다");
        }
        byte[] decoded = Base64.getMimeDecoder().decode(body.path("content").asText());
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private JsonNode getJson(
            String operation,
            Function<UriBuilder, URI> uriFunction
    ) {
        return ExternalResponses.parseJson(objectMapper, GITHUB, operation, getText(operation, uriFunction));
    }

    private String getText(
            String operation,
            Function<UriBuilder, URI> uriFunction
    ) {
        return externalCallExecutor.execute(GITHUB, operation, () -> restClient.get()
                .uri(uriFunction)
                .exchange((request, response) -> readText(operation, response)));
    }

    private String readText(
            String operation,
            ConvertibleClientHttpResponse response
    ) throws IOException {
        int status = response.getStatusCode().value();
        if (status == STATUS_FORBIDDEN || status == STATUS_TOO_MANY_REQUESTS) {
            Duration wait = rateLimitWait(response.getHeaders());
            if (wait != null) {
                throw new RetryableResponseException(status, wait);
            }
            if (status == STATUS_FORBIDDEN) {
                throw ExternalResponses.nonRetryable(GITHUB, operation, status);
            }
        }
        return ExternalResponses.readText(GITHUB, operation, response, clock);
    }

    private Duration rateLimitWait(HttpHeaders headers) {
        Duration retryAfter = RetryAfterParser.parse(headers, clock);
        if (retryAfter != null) {
            return retryAfter;
        }

        String reset = headers.getFirst(RATE_LIMIT_RESET_HEADER);
        if (RATE_LIMIT_EXHAUSTED.equals(headers.getFirst(RATE_LIMIT_REMAINING_HEADER)) && reset != null && reset.matches("\\d+")) {
            Instant resetAt = Instant.ofEpochSecond(Long.parseLong(reset));
            return RetryAfterParser.nonNegative(Duration.between(clock.instant(), resetAt));
        }
        return null;
    }

    private String repoPath(GithubRepo repo) {
        return "/repos/" + githubProps.owner() + "/" + repo.getRepoName();
    }
}
