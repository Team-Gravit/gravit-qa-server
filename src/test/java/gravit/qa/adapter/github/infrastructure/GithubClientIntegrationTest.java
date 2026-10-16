package gravit.qa.adapter.github.infrastructure;

import gravit.qa.adapter.exception.ExternalApiException;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.exception.ReleaseBranchNotFoundException;
import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.adapter.github.dto.internal.GithubCheckRunDto;
import gravit.qa.adapter.github.dto.internal.GithubCompareDto;
import gravit.qa.adapter.github.support.AosVersionParser;
import gravit.qa.adapter.github.dto.internal.AosVersionDto;
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
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.StringJoiner;

import static gravit.qa.support.HttpStubServer.StubResponse.json;
import static gravit.qa.support.HttpStubServer.StubResponse.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
class GithubClientIntegrationTest {

    private static final String BRANCH_PATH = "/repos/Team-Gravit/gravit-aos/branches/release";
    private static final String HEAD_SHA = "1111111111111111111111111111111111111111";
    private static final String BASE_SHA = "2222222222222222222222222222222222222222";
    private static final String BRANCH_BODY = """
            {"name": "release", "commit": {"sha": "%s"}}
            """.formatted(HEAD_SHA);

    @Autowired
    private GithubClient githubClient;

    @Autowired
    private HttpStubServer externalApiStubServer;

    @Autowired
    private RecordingSleeper recordingSleeper;

    @Nested
    @DisplayName("release 브랜치 HEAD를 조회할 때")
    class 브랜치_조회 {

        @Test
        void 커밋_SHA를_반환하고_인증과_버전_헤더를_보낸다() {
            // given
            externalApiStubServer.stub("GET", BRANCH_PATH, json(200, BRANCH_BODY));

            // when
            String sha = githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "release");

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
        void 브랜치가_없으면_재시도하지_않고_ReleaseBranchNotFoundException() {
            // given
            externalApiStubServer.stub("GET", BRANCH_PATH, status(404));

            // when & then
            assertThatThrownBy(() -> githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "release"))
                    .isInstanceOf(ReleaseBranchNotFoundException.class)
                    .isInstanceOf(NonRetryableJobException.class)
                    .extracting(e -> ((ReleaseBranchNotFoundException) e).getRepo())
                    .isEqualTo(GithubRepo.GRAVIT_AOS);
            assertThat(externalApiStubServer.requests()).hasSize(1);
        }

        @Test
        void 권한_오류_403은_재시도하지_않는다() {
            // given
            externalApiStubServer.stub("GET", BRANCH_PATH, status(403));

            // when & then
            assertThatThrownBy(() -> githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "release"))
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
            String sha = githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "release");

            // then
            assertThat(sha).isEqualTo(HEAD_SHA);
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
        }

        @Test
        void 서버_오류가_재시도_3회_후에도_이어지면_재시도_가능한_실패() {
            // given
            externalApiStubServer.stub("GET", BRANCH_PATH, status(500));

            // when & then
            assertThatThrownBy(() -> githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "release"))
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
            String sha = githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "release");

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
            assertThatThrownBy(() -> githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "release"))
                    .isInstanceOf(ExternalApiException.class);
            assertThat(externalApiStubServer.requests()).hasSize(1);
            assertThat(recordingSleeper.sleeps()).isEmpty();
        }

        @Test
        void 보조_한도_429는_Retry_After를_따른다() {
            // given
            externalApiStubServer.stub("GET", BRANCH_PATH, status(429).withHeader("Retry-After", "5"), json(200, BRANCH_BODY));

            // when
            githubClient.getBranchHeadSha(GithubRepo.GRAVIT_AOS, "release");

            // then
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(5));
        }
    }

    @Nested
    @DisplayName("변경 내역과 파일을 조회할 때")
    class 변경_조회 {

        @Test
        void 두_SHA_사이의_변경_파일과_패치를_읽는다() {
            // given
            externalApiStubServer.stub("GET", "/repos/Team-Gravit/gravit-server/compare/" + BASE_SHA + "..." + HEAD_SHA, json(200, """
                    {
                      "status": "ahead", "ahead_by": 3, "behind_by": 0, "total_commits": 3,
                      "files": [
                        {"filename": "src/A.java", "status": "modified", "additions": 2, "deletions": 1, "changes": 3, "patch": "@@ -1 +1,2 @@"},
                        {"filename": "src/B.java", "status": "renamed", "additions": 0, "deletions": 0, "changes": 0, "previous_filename": "src/Old.java"}
                      ]
                    }
                    """));

            // when
            GithubCompareDto compare = githubClient.compare(GithubRepo.GRAVIT_SERVER, BASE_SHA, HEAD_SHA);

            // then
            assertSoftly(softly -> {
                softly.assertThat(compare.status()).isEqualTo("ahead");
                softly.assertThat(compare.totalCommits()).isEqualTo(3);
                softly.assertThat(compare.files()).hasSize(2);
                softly.assertThat(compare.files().get(0).patch()).isEqualTo("@@ -1 +1,2 @@");
                softly.assertThat(compare.files().get(1).patch()).isNull();
                softly.assertThat(compare.files().get(1).previousFilename()).isEqualTo("src/Old.java");
                softly.assertThat(compare.rawJson()).contains("\"total_commits\"");
            });
        }

        @Test
        void 특정_ref의_build_gradle_kts를_내려받아_버전을_추출한다() {
            // given
            String gradle = "android {\n  defaultConfig {\n    versionCode = 1\n    versionName = \"1.0.0\"\n  }\n}\n";
            String encoded = Base64.getMimeEncoder().encodeToString(gradle.getBytes(StandardCharsets.UTF_8));
            externalApiStubServer.stub("GET", "/repos/Team-Gravit/gravit-aos/contents/app/build.gradle.kts",
                    json(200, "{\"encoding\": \"base64\", \"content\": \"" + encoded.replace("\r\n", "\\n") + "\"}"));

            // when
            String content = githubClient.getFileContent(GithubRepo.GRAVIT_AOS, AosVersionParser.BUILD_GRADLE_PATH, HEAD_SHA);
            AosVersionDto version = AosVersionParser.parse(content, 1);

            // then
            assertThat(content).isEqualTo(gradle);
            assertThat(externalApiStubServer.requests().getFirst().query()).isEqualTo("ref=" + HEAD_SHA);
            assertThat(version.versionCode()).isEqualTo(1);
            assertThat(version.versionCodeWarning()).isTrue();
        }

        @Test
        void check_runs를_페이지를_넘기며_모두_읽는다() {
            // given
            String path = "/repos/Team-Gravit/gravit-server/commits/" + HEAD_SHA + "/check-runs";
            externalApiStubServer.stub("GET", path, json(200, checkRunsPage(100, 101)), json(200, checkRunsPage(1, 101)));

            // when
            List<GithubCheckRunDto> checkRuns = githubClient.getCheckRuns(GithubRepo.GRAVIT_SERVER, HEAD_SHA);

            // then
            assertSoftly(softly -> {
                softly.assertThat(checkRuns).hasSize(101);
                softly.assertThat(checkRuns.getFirst().conclusion()).isEqualTo("success");
                softly.assertThat(checkRuns.getFirst().completedAt()).isEqualTo(OffsetDateTime.parse("2026-10-16T03:00:00Z"));
                softly.assertThat(externalApiStubServer.requests()).extracting(RecordedRequest::query)
                        .containsExactly("per_page=100&page=1", "per_page=100&page=2");
            });
        }

        private String checkRunsPage(
                int size,
                int total
        ) {
            StringJoiner items = new StringJoiner(",");
            for (int i = 0; i < size; i++) {
                items.add("""
                        {"name": "공통 CI %d", "status": "completed", "conclusion": "success",
                         "details_url": "https://github.com/x", "completed_at": "2026-10-16T03:00:00Z"}
                        """.formatted(i));
            }
            return "{\"total_count\": " + total + ", \"check_runs\": [" + items + "]}";
        }
    }
}
