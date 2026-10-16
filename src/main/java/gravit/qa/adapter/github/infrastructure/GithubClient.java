package gravit.qa.adapter.github.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.domain.ExternalApi;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.exception.ReleaseBranchNotFoundException;
import gravit.qa.adapter.github.config.GithubProps;
import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.adapter.github.dto.internal.GithubChangedFileDto;
import gravit.qa.adapter.github.dto.internal.GithubCheckRunDto;
import gravit.qa.adapter.github.dto.internal.GithubCompareDto;
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
import java.time.OffsetDateTime;
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
    private static final String COMPARE_PATH = REPO_PATH + "/compare/{base}...{head}";
    private static final String CONTENTS_PATH = REPO_PATH + "/contents/";
    private static final String CHECK_RUNS_PATH = REPO_PATH + "/commits/{ref}/check-runs";
    private static final String REF_PARAM = "ref";
    private static final String PER_PAGE_PARAM = "per_page";
    private static final String PAGE_PARAM = "page";
    private static final String BASE64_ENCODING = "base64";
    private static final int PAGE_SIZE = 100;
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
                throw new ReleaseBranchNotFoundException(repo, branch);
            }
            throw e;
        }
        return ExternalResponses.requireText(body, "/commit/sha", GITHUB, operation);
    }

    public GithubCompareDto compare(
            GithubRepo repo,
            String baseSha,
            String headSha
    ) {
        String operation = "GET " + repoPath(repo) + "/compare/" + baseSha + "..." + headSha;
        String rawJson = getText(operation, uri -> uri.path(COMPARE_PATH).build(githubProps.owner(), repo.getRepoName(), baseSha, headSha));
        JsonNode body = ExternalResponses.parseJson(objectMapper, GITHUB, operation, rawJson);

        List<GithubChangedFileDto> files = new ArrayList<>();
        body.path("files").forEach(file -> files.add(toChangedFile(file)));

        return new GithubCompareDto(
                baseSha,
                headSha,
                body.path("status").asText(null),
                body.path("ahead_by").asInt(),
                body.path("behind_by").asInt(),
                body.path("total_commits").asInt(),
                files,
                rawJson
        );
    }

    public String getFileContent(
            GithubRepo repo,
            String path,
            String ref
    ) {
        String operation = "GET " + repoPath(repo) + "/contents/" + path + "?ref=" + ref;
        JsonNode body = getJson(operation, uri -> uri.path(CONTENTS_PATH).path(path)
                .queryParam(REF_PARAM, ref)
                .build(githubProps.owner(), repo.getRepoName()));

        if (!BASE64_ENCODING.equals(body.path("encoding").asText())) {
            throw ExternalResponses.invalidResponse(GITHUB, operation, "base64가 아닌 인코딩(" + body.path("encoding").asText() + ")입니다");
        }
        byte[] decoded = Base64.getMimeDecoder().decode(body.path("content").asText());
        return new String(decoded, StandardCharsets.UTF_8);
    }

    public List<GithubCheckRunDto> getCheckRuns(
            GithubRepo repo,
            String ref
    ) {
        String operation = "GET " + repoPath(repo) + "/commits/" + ref + "/check-runs";
        List<GithubCheckRunDto> checkRuns = new ArrayList<>();
        for (int page = 1; ; page++) {
            int currentPage = page;
            JsonNode body = getJson(operation + "?page=" + currentPage, uri -> uri.path(CHECK_RUNS_PATH)
                    .queryParam(PER_PAGE_PARAM, PAGE_SIZE)
                    .queryParam(PAGE_PARAM, currentPage)
                    .build(githubProps.owner(), repo.getRepoName(), ref));

            JsonNode items = body.path("check_runs");
            items.forEach(item -> checkRuns.add(toCheckRun(item)));
            if (items.size() < PAGE_SIZE || checkRuns.size() >= body.path("total_count").asInt()) {
                return checkRuns;
            }
        }
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

    private GithubChangedFileDto toChangedFile(JsonNode file) {
        return new GithubChangedFileDto(
                file.path("filename").asText(),
                file.path("status").asText(),
                file.path("additions").asInt(),
                file.path("deletions").asInt(),
                file.path("changes").asInt(),
                file.path("patch").asText(null),
                file.path("previous_filename").asText(null)
        );
    }

    private GithubCheckRunDto toCheckRun(JsonNode checkRun) {
        String completedAt = checkRun.path("completed_at").asText(null);
        return new GithubCheckRunDto(
                checkRun.path("name").asText(),
                checkRun.path("status").asText(),
                checkRun.path("conclusion").asText(null),
                checkRun.path("details_url").asText(null),
                completedAt == null ? null : OffsetDateTime.parse(completedAt)
        );
    }

    private String repoPath(GithubRepo repo) {
        return "/repos/" + githubProps.owner() + "/" + repo.getRepoName();
    }
}
