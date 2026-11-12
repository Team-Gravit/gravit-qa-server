package gravit.qa.autoReview.support;

import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.autoReview.dto.internal.ApiEndpointDto;
import gravit.qa.autoReview.dto.internal.AutoReviewJudgmentDto;
import gravit.qa.autoReview.dto.internal.CodeChunksDto;
import gravit.qa.autoReview.dto.internal.SourceFileDto;
import gravit.qa.checklist.domain.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class CodeSelectorRulesTest {

    @Nested
    @DisplayName("서버 코드를 고를 때")
    class 서버 {

        private static final String CONTROLLER = "src/main/java/gravit/code/auth/controller/OAuthController.java";
        private static final String DOCS = "src/main/java/gravit/code/auth/controller/docs/OAuthControllerDocs.java";
        private static final String SERVICE = "src/main/java/gravit/code/auth/service/OAuthService.java";
        private static final String REPOSITORY = "src/main/java/gravit/code/auth/repository/UserRepository.java";
        private static final String RESPONSE = "src/main/java/gravit/code/auth/dto/response/LoginUrlResponse.java";
        private static final String OTHER = "src/main/java/gravit/code/friend/controller/FriendController.java";
        private static final Map<String, String> SOURCES = Map.of(
                CONTROLLER, """
                        @RestController
                        @RequestMapping(value = "/api/v1/oauth")
                        public class OAuthController implements OAuthControllerDocs {
                            private final OAuthService oAuthService;

                            @GetMapping("/login-url/{provider}")
                            public LoginUrlResponse loginUrl(@PathVariable String provider) {
                                return oAuthService.loginUrl(provider);
                            }
                        }
                        """,
                SERVICE, "public class OAuthService {\n    private final UserRepository userRepository;\n}\n",
                REPOSITORY, "public interface UserRepository {}\n",
                RESPONSE, "public record LoginUrlResponse(String url) {}\n",
                DOCS, "public interface OAuthControllerDocs {}\n",
                OTHER, "@RestController\n@RequestMapping(\"/api/v1/friends\")\npublic class FriendController {\n    @GetMapping\n    public void list() {}\n}\n"
        );

        @Test
        void 관련_API_경로가_맞는_컨트롤러에서_서비스와_DTO_저장소를_깊이순으로_따라간다() {
            // when
            List<String> paths = ServerCodeSelector.select(SOURCES.keySet(), path -> Optional.ofNullable(SOURCES.get(path)),
                    List.of(new ApiEndpointDto("GET", "/api/v1/oauth/login-url/{}")));

            // then
            assertThat(paths).containsExactly(CONTROLLER, RESPONSE, SERVICE, REPOSITORY);
        }

        @Test
        void 메서드가_다르면_고르지_않는다() {
            // when & then
            assertThat(ServerCodeSelector.select(SOURCES.keySet(), path -> Optional.ofNullable(SOURCES.get(path)),
                    List.of(new ApiEndpointDto("POST", "/api/v1/oauth/login-url/{}")))).isEmpty();
        }

        @Test
        void 경로_변수_이름과_끝_슬래시는_무시하고_비교한다() {
            // when & then
            assertThat(ApiPaths.parse("GET /api/v1/oauth/login-url/{kind}/")).contains(new ApiEndpointDto("GET", "/api/v1/oauth/login-url/{}"));
            assertThat(ApiPaths.parse("잘못된 줄")).isEmpty();
        }
    }

    @Nested
    @DisplayName("웹 코드를 고를 때")
    class 웹 {

        @Test
        void 파일_라우트를_URL로_바꿀_때_밑줄_세그먼트와_index_route를_빼고_경로_변수를_맞춘다() {
            // when & then
            assertSoftly(softly -> {
                softly.assertThat(WebCodeSelector.routeOf(WebCodeSelector.ROUTES_ROOT + "_authenticated/_app-shell/my/social.tsx")).contains("/my/social");
                softly.assertThat(WebCodeSelector.routeOf(WebCodeSelector.ROUTES_ROOT + "_authenticated/_focus/learning.lessons.$lessonId.tsx")).contains("/learning/lessons/$");
                softly.assertThat(WebCodeSelector.routeOf(WebCodeSelector.ROUTES_ROOT + "_authenticated/_focus/learning.lessons.$lessonId_.result.$submissionId.tsx")).contains("/learning/lessons/$/result/$");
                softly.assertThat(WebCodeSelector.routeOf(WebCodeSelector.ROUTES_ROOT + "_authenticated/_app-shell/my/route.tsx")).contains("/my");
                softly.assertThat(WebCodeSelector.routeOf(WebCodeSelector.ROUTES_ROOT + "_authenticated/_app-shell/-concept-note-route.ts")).isEmpty();
                softly.assertThat(WebCodeSelector.routesOf("/my, /my/summary")).containsExactly("/my", "/my/summary");
                softly.assertThat(WebCodeSelector.routesOf("/learning/lessons/$lessonId")).containsExactly("/learning/lessons/$");
            });
        }

        @Test
        void 라우트_파일에서_별칭과_상대_import를_따라가고_공용_UI와_테스트는_뺀다() {
            // given
            String route = WebCodeSelector.ROUTES_ROOT + "_authenticated/_app-shell/my/social.tsx";
            String page = "apps/web/src/pages/my/ui/SocialPage.tsx";
            String widget = "apps/web/src/widgets/social/index.ts";
            String sharedUi = "apps/web/src/shared/ui/Button.tsx";
            Map<String, String> sources = Map.of(
                    route, "import { SocialPage } from '@/pages/my/ui/SocialPage';\nimport { Button } from '@/shared/ui/Button';\n",
                    page, "import { FollowList } from '../../../widgets/social';\n",
                    widget, "export const FollowList = () => null;\n",
                    sharedUi, "export const Button = () => null;\n",
                    "apps/web/src/pages/my/ui/SocialPage.test.tsx", "import x from './SocialPage';\n"
            );

            // when
            List<String> paths = WebCodeSelector.select(sources.keySet(), path -> Optional.ofNullable(sources.get(path)), "/my/social");

            // then
            assertThat(paths).containsExactly(route, page, widget);
        }
    }

    @Nested
    @DisplayName("앱 코드를 고를 때")
    class 앱 {

        @Test
        void Retrofit_경로가_맞는_호출부와_그_클래스를_쓰는_화면을_고르고_API_파일은_발췌만_넣는다() {
            // given
            String api = "app/src/main/java/com/example/gravit/api/ApiService.kt";
            String viewModel = "app/src/main/java/com/example/gravit/main/User/MyPageVM.kt";
            String screen = "app/src/main/java/com/example/gravit/main/User/MyPage.kt";
            String unrelated = "app/src/main/java/com/example/gravit/main/Home/HomeScreen.kt";
            Map<String, String> sources = Map.of(
                    api, "interface ApiService {\n    @GET(\"api/v1/friends/count\")\n    suspend fun getFriendCount(): Count\n\n    @GET(\"api/v1/other\")\n    suspend fun other(): Other\n}\n",
                    viewModel, "class MyPageVM {\n    fun load() = api.getFriendCount()\n}\n",
                    screen, "@Composable\nfun MyPage(vm: MyPageVM) {}\n",
                    unrelated, "fun HomeScreen() {}\n"
            );

            // when
            List<SourceFileDto> files = AppCodeSelector.select(sources.keySet(), path -> Optional.ofNullable(sources.get(path)), List.of(), path -> Optional.empty(),
                    List.of(new ApiEndpointDto("GET", "/api/v1/friends/count")), false);

            // then
            assertSoftly(softly -> {
                softly.assertThat(files).extracting(SourceFileDto::path).containsExactly(api, viewModel, screen);
                softly.assertThat(files.getFirst().firstLine()).isEqualTo(2);
                softly.assertThat(files.getFirst().lines().getFirst()).contains("@GET(\"api/v1/friends/count\")");
            });
        }
    }

    @Nested
    @DisplayName("코드를 나눠 담을 때")
    class 나누기 {

        @Test
        void 상한을_넘는_파일은_줄_범위로_나누고_묶음_수를_넘는_파일은_빠진_파일로_남긴다() {
            // given
            SourceFileDto big = SourceFileDto.of(GithubRepo.GRAVIT_AOS, "Big.kt", Collections.nCopies(40, "x".repeat(40)), 1);
            SourceFileDto small = SourceFileDto.of(GithubRepo.GRAVIT_AOS, "Small.kt", List.of("y"), 1);

            // when
            CodeChunksDto chunks = CodeChunker.chunk(List.of(big, small), 1000, 2);

            // then
            assertSoftly(softly -> {
                softly.assertThat(chunks.chunks()).hasSize(2);
                softly.assertThat(chunks.chunks().getFirst().slices().getFirst().firstLine()).isEqualTo(1);
                softly.assertThat(chunks.chunks().get(1).slices().getFirst().firstLine()).isGreaterThan(1);
                softly.assertThat(chunks.droppedFiles()).isNotEmpty();
                softly.assertThat(chunks.chunks().getFirst().rendered()).contains("### gravit-aos/Big.kt (1-").contains("    1| ");
            });
        }
    }

    @Nested
    @DisplayName("나눠 판정한 결과를 합칠 때")
    class 합치기 {

        @Test
        void 근거_있는_FAIL을_PASS보다_앞세우고_모두_판정_불가면_판정_불가로_둔다() {
            // when & then
            assertThat(AutoReviewer.merge(1, List.of(
                    AutoReviewJudgmentDto.of(1, Verdict.PASS, "있음"),
                    AutoReviewJudgmentDto.of(1, Verdict.FAIL, "다름"),
                    AutoReviewJudgmentDto.of(1, Verdict.UNKNOWN, "없음")
            )).verdict()).isEqualTo(Verdict.FAIL);
            assertThat(AutoReviewer.merge(1, List.of(
                    AutoReviewJudgmentDto.of(1, Verdict.UNKNOWN, "없음"),
                    AutoReviewJudgmentDto.of(1, Verdict.PASS, "있음")
            )).verdict()).isEqualTo(Verdict.PASS);
            assertThat(AutoReviewer.merge(1, List.of(AutoReviewJudgmentDto.of(1, Verdict.UNKNOWN, "없음"))).reason()).isEqualTo("없음");
        }
    }
}
