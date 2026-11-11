package gravit.qa.adapter.github.infrastructure;

import gravit.qa.adapter.exception.BranchNotFoundException;
import gravit.qa.adapter.exception.ExternalApiException;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.adapter.github.dto.internal.GithubTreeDto;
import gravit.qa.adapter.github.dto.internal.GithubTreeEntryDto;
import gravit.qa.job.support.NonRetryableJobException;
import gravit.qa.support.HttpStubServer;
import gravit.qa.support.HttpStubServer.RecordedRequest;
import gravit.qa.support.QaSpringBootTest;
import gravit.qa.support.RecordingSleeper;
import gravit.qa.support.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static gravit.qa.support.HttpStubServer.StubResponse.json;
import static gravit.qa.support.HttpStubServer.StubResponse.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
class GithubClientIntegrationTest {

    private static final String BRANCH_PATH = "/repos/Team-Gravit/gravit-aos/branches/qa-baseline";
    private static final String HEAD_SHA = "1111111111111111111111111111111111111111";
    private static final String BASE_SHA = "2222222222222222222222222222222222222222";
    private static final String BRANCH_BODY = """
            {"name": "qa-baseline", "commit": {"sha": "%s"}}
            """.formatted(HEAD_SHA);

    @Autowired
    private GithubClient githubClient;

    @Autowired
    private HttpStubServer externalApiStubServer;

    @Autowired
    private RecordingSleeper recordingSleeper;

    @Nested
    @DisplayName("QA 기준 브랜치 HEAD를 조회할 때")
    class 브랜치_조회 {

        @Test
        void 커밋_SHA를_반환하고_인증과_버전_헤더를_보낸다() {
            // given
            externalApiStubServer.stub("GET", BRANCH_PATH, json(200, BRANCH_BODY));

            // when
            String sha = githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "qa-baseline");

            // then
            RecordedRequest request = externalApiStubServer.requests().getFirst();
            assertSoftly(softly -> {
                softly.assertThat(sha).isEqualTo(HEAD_SHA);
                softly.assertThat(request.header("Authorization")).isEqualTo("Bearer test-github-token");
                softly.assertThat(request.header("Accept")).isEqualTo("application/vnd.github+json");
                softly.assertThat(request.header("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
            });
        }

        @Test
        void 브랜치가_없으면_재시도하지_않고_BranchNotFoundException() {
            // given
            externalApiStubServer.stub("GET", BRANCH_PATH, status(404));

            // when & then
            assertThatThrownBy(() -> githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "qa-baseline"))
                    .isInstanceOf(BranchNotFoundException.class)
                    .isInstanceOf(NonRetryableJobException.class)
                    .extracting(e -> ((BranchNotFoundException) e).getRepo())
                    .isEqualTo(GithubRepo.GRAVIT_AOS);
            assertThat(externalApiStubServer.requests()).hasSize(1);
        }

        @Test
        void 권한_오류_403은_재시도하지_않는다() {
            // given
            externalApiStubServer.stub("GET", BRANCH_PATH, status(403));

            // when & then
            assertThatThrownBy(() -> githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "qa-baseline"))
                    .isInstanceOf(NonRetryableExternalApiException.class)
                    .hasMessageNotContaining("test-github-token")
                    .extracting(e -> ((NonRetryableExternalApiException) e).getStatus())
                    .isEqualTo(403);
            assertThat(externalApiStubServer.requests()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("재시도 규칙에 따라")
    class 재시도 {

        @Test
        void 서버_오류는_지수_백오프로_재시도한다() {
            // given
            externalApiStubServer.stub("GET", BRANCH_PATH, status(503), status(502), json(200, BRANCH_BODY));

            // when
            String sha = githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "qa-baseline");

            // then
            assertThat(sha).isEqualTo(HEAD_SHA);
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
        }

        @Test
        void 서버_오류가_재시도_3회_후에도_이어지면_재시도_가능한_실패() {
            // given
            externalApiStubServer.stub("GET", BRANCH_PATH, status(500));

            // when & then
            assertThatThrownBy(() -> githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "qa-baseline"))
                    .isInstanceOf(ExternalApiException.class)
                    .isNotInstanceOf(NonRetryableJobException.class);
            assertThat(externalApiStubServer.requests()).hasSize(4);
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
        }

        @Test
        void 사용_한도_403은_reset_시각까지_기다린_뒤_재시도한다() {
            // given
            long resetAt = TestClockConfig.BASE_INSTANT.plusSeconds(30).getEpochSecond();
            externalApiStubServer.stub("GET", BRANCH_PATH,
                    status(403).withHeader("x-ratelimit-remaining", "0").withHeader("x-ratelimit-reset", String.valueOf(resetAt)),
                    json(200, BRANCH_BODY));

            // when
            String sha = githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "qa-baseline");

            // then
            assertThat(sha).isEqualTo(HEAD_SHA);
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(30));
        }

        @Test
        void 대기_시간이_한도를_넘으면_기다리지_않고_재시도_가능한_실패() {
            // given
            long resetAt = TestClockConfig.BASE_INSTANT.plusSeconds(600).getEpochSecond();
            externalApiStubServer.stub("GET", BRANCH_PATH,
                    status(403).withHeader("x-ratelimit-remaining", "0").withHeader("x-ratelimit-reset", String.valueOf(resetAt)));

            // when & then
            assertThatThrownBy(() -> githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "qa-baseline"))
                    .isInstanceOf(ExternalApiException.class);
            assertThat(externalApiStubServer.requests()).hasSize(1);
            assertThat(recordingSleeper.sleeps()).isEmpty();
        }

        @Test
        void 보조_한도_429는_Retry_After를_따른다() {
            // given
            externalApiStubServer.stub("GET", BRANCH_PATH, status(429).withHeader("Retry-After", "5"), json(200, BRANCH_BODY));

            // when
            githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "qa-baseline");

            // then
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(5));
        }
    }

    @Nested
    @DisplayName("파일 목록과 내용을 조회할 때")
    class 파일_조회 {

        @Test
        void 커밋의_파일_목록을_재귀로_읽고_파일만_남긴다() {
            // given
            externalApiStubServer.stub("GET", "/repos/Team-Gravit/gravit-server/git/trees/" + HEAD_SHA, json(200, """
                    {"sha": "%s", "truncated": false, "tree": [
                      {"path": "src", "type": "tree", "sha": "aaaa"},
                      {"path": "src/A.java", "type": "blob", "sha": "%s", "size": 120}
                    ]}
                    """.formatted(HEAD_SHA, BASE_SHA)));

            // when
            GithubTreeDto tree = githubClient.getTree(GithubRepo.GRAVIT_SERVER, HEAD_SHA);

            // then
            assertSoftly(softly -> {
                softly.assertThat(tree.truncated()).isFalse();
                softly.assertThat(tree.entries()).containsExactly(new GithubTreeEntryDto("src/A.java", BASE_SHA, 120));
                softly.assertThat(externalApiStubServer.requests().getFirst().query()).isEqualTo("recursive=1");
            });
        }

        @Test
        void 파일_내용을_blob_SHA로_내려받아_디코딩한다() {
            // given
            String source = "class A {\n}\n".translateEscapes();
            String encoded = Base64.getMimeEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));
            externalApiStubServer.stub("GET", "/repos/Team-Gravit/gravit-server/git/blobs/" + BASE_SHA,
                    json(200, "{\"encoding\": \"base64\", \"content\": \"" + encoded.replace("\r\n", "\\n") + "\"}"));

            // when
            String content = githubClient.getBlob(GithubRepo.GRAVIT_SERVER, BASE_SHA);

            // then
            assertThat(content).isEqualTo(source);
        }
    }
}
