package gravit.qa.autoReview.support;

import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.autoReview.dto.internal.ApiEndpointDto;
import gravit.qa.autoReview.dto.internal.AutoReviewJudgmentDto;
import gravit.qa.autoReview.dto.internal.AutoReviewResultOutputDto;
import gravit.qa.autoReview.dto.internal.CodeChunksDto;
import gravit.qa.autoReview.dto.internal.CodeEvidenceOutputDto;
import gravit.qa.autoReview.dto.internal.SourceFileDto;
import gravit.qa.checklist.domain.Verdict;
import gravit.qa.checklist.dto.internal.CodeEvidenceDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
                    List.of(new ApiEndpointDto("GET", "/api/v1/oauth/login-url/{}"))).stream().map(SourceFileDto::path).toList();

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
    @DisplayName("서버 코드에서 이름이 같은 클래스를 가를 때")
    class 서버_이름_해석 {

        private static final String ROOT = "src/main/java/gravit/code/";
        private static final String CONTROLLER = ROOT + "notice/controller/NoticeQueryController.java";
        private static final String DOCS = ROOT + "notice/controller/docs/NoticeQueryControllerDocs.java";
        private static final String SERVICE = ROOT + "notice/service/NoticeQueryService.java";
        private static final String REPOSITORY = ROOT + "notice/repository/NoticeRepository.java";
        private static final String DETAIL = ROOT + "notice/dto/response/NoticeDetailResponse.java";
        private static final String ADMIN_DETAIL = ROOT + "admin/dto/response/NoticeDetailResponse.java";
        private static final String SUMMARY = ROOT + "notice/dto/response/NoticeSummaryResponse.java";
        private static final String PAGE = ROOT + "global/dto/response/PageResponse.java";
        private static final String ERROR_CODE = ROOT + "global/exception/domain/CustomErrorCode.java";
        private static final ApiEndpointDto NOTICE_DETAIL_API = new ApiEndpointDto("GET", "/api/v1/notice/{}");
        private static final String CONTROLLER_SOURCE = """
                package gravit.code.notice.controller;

                import gravit.code.global.dto.response.PageResponse;
                import gravit.code.notice.controller.docs.NoticeQueryControllerDocs;
                import gravit.code.notice.dto.response.NoticeDetailResponse;
                import gravit.code.notice.dto.response.NoticeSummaryResponse;
                import gravit.code.notice.service.NoticeQueryService;
                import org.springframework.http.ResponseEntity;

                @RestController
                @RequestMapping("/api/v1/notice")
                public class NoticeQueryController implements NoticeQueryControllerDocs {

                    private final NoticeQueryService noticeQueryService;

                    @GetMapping("/summaries/{page}")
                    public ResponseEntity<PageResponse<NoticeSummaryResponse>> getNoticeSummaries(@PathVariable("page") int page) {
                        return ResponseEntity.status(OK).body(noticeQueryService.getNoticeSummaries(page));
                    }

                    @GetMapping("/{noticeId}")
                    public ResponseEntity<NoticeDetailResponse> getNoticeSummary(@PathVariable("noticeId") Long noticeId) {
                        return ResponseEntity.status(OK).body(noticeQueryService.getNoticeDetail(noticeId));
                    }
                }
                """;
        private static final String SERVICE_SOURCE = """
                package gravit.code.notice.service;

                import gravit.code.notice.dto.response.NoticeDetailResponse;
                import gravit.code.notice.repository.NoticeRepository;

                import static gravit.code.global.exception.domain.CustomErrorCode.NOTICE_NOT_FOUND;
                import static gravit.code.global.exception.domain.CustomErrorCode.PAGE_MUST_START_FROM_1;
                import static org.springframework.http.HttpStatus.OK;

                @Service
                public class NoticeQueryService {

                    private final NoticeRepository noticeRepository;

                    public NoticeDetailResponse getNoticeDetail(long noticeId) {
                        return NoticeDetailResponse.from(noticeRepository.findById(noticeId).orElseThrow(() -> new RestApiException(NOTICE_NOT_FOUND)));
                    }
                }
                """;
        private static final String ERROR_CODE_SOURCE = """
                package gravit.code.global.exception.domain;

                import static org.springframework.http.HttpStatus.BAD_REQUEST;
                import static org.springframework.http.HttpStatus.NOT_FOUND;

                public enum CustomErrorCode {
                    UNIT_NOT_FOUND(NOT_FOUND, "UNIT_4041", "존재하지 않는 유닛입니다."),
                    NOTICE_NOT_FOUND(NOT_FOUND, "NOTICE_4041", "존재하지 않는 공지사항입니다."),
                    PAGE_MUST_START_FROM_1(
                            BAD_REQUEST,
                            "PAGE_4001",
                            "notice 페이지는 1번부터 시작합니다."
                    );
                }
                """;
        private static final Map<String, String> SOURCES = Map.of(
                CONTROLLER, CONTROLLER_SOURCE,
                DOCS, "package gravit.code.notice.controller.docs;\n\npublic interface NoticeQueryControllerDocs {}\n",
                SERVICE, SERVICE_SOURCE,
                REPOSITORY, "package gravit.code.notice.repository;\n\npublic interface NoticeRepository {}\n",
                DETAIL, "package gravit.code.notice.dto.response;\n\npublic record NoticeDetailResponse(long id, String authorName) {}\n",
                ADMIN_DETAIL, "package gravit.code.admin.dto.response;\n\npublic record NoticeDetailResponse(long noticeId, String summary) {}\n",
                SUMMARY, "package gravit.code.notice.dto.response;\n\npublic record NoticeSummaryResponse(long id) {}\n",
                PAGE, "package gravit.code.global.dto.response;\n\npublic record PageResponse<T>(int page) {}\n",
                ERROR_CODE, ERROR_CODE_SOURCE
        );

        @Test
        void NOTC_02처럼_이름이_같은_관리자_DTO가_있어도_import한_DTO를_고르고_오류_코드는_선언_줄만_넣는다() {
            // when
            List<SourceFileDto> files = 고르기(SOURCES, NOTICE_DETAIL_API);

            // then
            assertThat(files)
                    .extracting(SourceFileDto::path, SourceFileDto::firstLine, SourceFileDto::lastLine)
                    .containsExactly(
                            tuple(CONTROLLER, 1, 26),
                            tuple(PAGE, 1, 4),
                            tuple(DETAIL, 1, 4),
                            tuple(SUMMARY, 1, 4),
                            tuple(SERVICE, 1, 19),
                            tuple(REPOSITORY, 1, 4),
                            tuple(ERROR_CODE, 8, 8),
                            tuple(ERROR_CODE, 9, 13)
                    );
            assertSoftly(softly -> {
                softly.assertThat(files.get(6).lines()).containsExactly("    NOTICE_NOT_FOUND(NOT_FOUND, \"NOTICE_4041\", \"존재하지 않는 공지사항입니다.\"),");
                softly.assertThat(files.get(7).lines().getFirst()).contains("PAGE_MUST_START_FROM_1(");
                softly.assertThat(files.get(7).lines().getLast()).contains(");");
            });
        }

        @Test
        void import_없이_같은_패키지_클래스를_참조하면_같은_패키지_파일을_고른다() {
            // given
            String validator = ROOT + "notice/controller/NoticeValidator.java";
            String adminValidator = ROOT + "admin/controller/NoticeValidator.java";
            Map<String, String> sources = Map.of(
                    CONTROLLER, "package gravit.code.notice.controller;\n\n@RequestMapping(\"/api/v1/notice\")\npublic class NoticeQueryController {\n    private final NoticeValidator noticeValidator;\n\n    @GetMapping(\"/{noticeId}\")\n    public void get() {}\n}\n",
                    validator, "package gravit.code.notice.controller;\n\npublic class NoticeValidator {}\n",
                    adminValidator, "package gravit.code.admin.controller;\n\npublic class NoticeValidator {}\n"
            );

            // when & then
            assertThat(고르기(sources, NOTICE_DETAIL_API)).extracting(SourceFileDto::path).containsExactly(CONTROLLER, validator);
        }

        @Test
        void 와일드카드_import면_그_패키지의_파일을_고른다() {
            // given
            Map<String, String> sources = Map.of(
                    CONTROLLER, "package gravit.code.notice.controller;\n\nimport gravit.code.notice.dto.response.*;\n\n@RequestMapping(\"/api/v1/notice\")\npublic class NoticeQueryController {\n    @GetMapping(\"/{noticeId}\")\n    public NoticeDetailResponse get() {}\n}\n",
                    DETAIL, SOURCES.get(DETAIL),
                    ADMIN_DETAIL, SOURCES.get(ADMIN_DETAIL)
            );

            // when & then
            assertThat(고르기(sources, NOTICE_DETAIL_API)).extracting(SourceFileDto::path).containsExactly(CONTROLLER, DETAIL);
        }

        @Test
        void import로_정할_수_없고_이름이_겹치면_고르지_않는다() {
            // given
            Map<String, String> sources = Map.of(
                    CONTROLLER, "package gravit.code.notice.controller;\n\n@RequestMapping(\"/api/v1/notice\")\npublic class NoticeQueryController {\n    @GetMapping(\"/{noticeId}\")\n    public NoticeDetailResponse get() {}\n}\n",
                    DETAIL, SOURCES.get(DETAIL),
                    ADMIN_DETAIL, SOURCES.get(ADMIN_DETAIL)
            );

            // when & then
            assertThat(고르기(sources, NOTICE_DETAIL_API)).extracting(SourceFileDto::path).containsExactly(CONTROLLER);
        }

        @Test
        void 라이브러리_클래스를_import했으면_이름이_같은_저장소_파일을_고르지_않는다() {
            // given
            Map<String, String> sources = Map.of(
                    CONTROLLER, "package gravit.code.notice.controller;\n\nimport org.example.page.PageResponse;\n\n@RequestMapping(\"/api/v1/notice\")\npublic class NoticeQueryController {\n    @GetMapping(\"/{noticeId}\")\n    public PageResponse get() {}\n}\n",
                    PAGE, SOURCES.get(PAGE)
            );

            // when & then
            assertThat(고르기(sources, NOTICE_DETAIL_API)).extracting(SourceFileDto::path).containsExactly(CONTROLLER);
        }

        @Test
        void 이름이_같은_컨트롤러가_다른_패키지에_있어도_경로가_맞는_컨트롤러를_고른다() {
            // given
            String adminController = ROOT + "admin/controller/NoticeQueryController.java";
            Map<String, String> sources = Map.of(
                    CONTROLLER, "package gravit.code.notice.controller;\n\n@RequestMapping(\"/api/v1/notice\")\npublic class NoticeQueryController {\n    @GetMapping(\"/{noticeId}\")\n    public void get() {}\n}\n",
                    adminController, "package gravit.code.admin.controller;\n\n@RequestMapping(\"/api/v1/admin/notices\")\npublic class NoticeQueryController {\n    @GetMapping(\"/{noticeId}\")\n    public void get() {}\n}\n"
            );

            // when & then
            assertThat(고르기(sources, NOTICE_DETAIL_API)).extracting(SourceFileDto::path).containsExactly(CONTROLLER);
        }

        private List<SourceFileDto> 고르기(
                Map<String, String> sources,
                ApiEndpointDto endpoint
        ) {
            return ServerCodeSelector.select(sources.keySet(), path -> Optional.ofNullable(sources.get(path)), List.of(endpoint));
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
                    AutoReviewJudgmentDto.of(1, Verdict.PASS, "있음", List.of()),
                    AutoReviewJudgmentDto.of(1, Verdict.FAIL, "다름", List.of()),
                    AutoReviewJudgmentDto.of(1, Verdict.UNKNOWN, "없음", List.of())
            )).verdict()).isEqualTo(Verdict.FAIL);
            assertThat(AutoReviewer.merge(1, List.of(
                    AutoReviewJudgmentDto.of(1, Verdict.UNKNOWN, "없음", List.of()),
                    AutoReviewJudgmentDto.of(1, Verdict.PASS, "있음", List.of())
            )).verdict()).isEqualTo(Verdict.PASS);
            assertThat(AutoReviewer.merge(1, List.of(AutoReviewJudgmentDto.of(1, Verdict.UNKNOWN, "없음", List.of()))).reason()).isEqualTo("없음");
        }

        @Test
        void 근거_경로는_저장소_이름을_붙인_경로로_맞추고_합칠_때는_고른_판정의_근거를_가져간다() {
            // given
            String path = "src/main/java/gravit/code/notice/service/NoticeQueryService.java";
            String label = "gravit-server/" + path;
            SourceFileDto slice = SourceFileDto.of(GithubRepo.GRAVIT_SERVER, path, Collections.nCopies(60, "code"), 1);
            AutoReviewResultOutputDto bare = new AutoReviewResultOutputDto(1, List.of(new CodeEvidenceOutputDto(" " + path + " ", 46, 50)), "없는 공지는 404", Verdict.PASS);
            AutoReviewResultOutputDto labeled = new AutoReviewResultOutputDto(1, List.of(new CodeEvidenceOutputDto(label, 10, 12)), "이전, 다음 필드가 없다", Verdict.FAIL);

            // when
            List<CodeEvidenceDto> fromBare = AutoReviewer.evidenceOf(bare, List.of(slice));
            List<CodeEvidenceDto> fromLabel = AutoReviewer.evidenceOf(labeled, List.of(slice));
            AutoReviewJudgmentDto merged = AutoReviewer.merge(1, List.of(
                    AutoReviewJudgmentDto.of(1, Verdict.PASS, "없는 공지는 404", fromBare),
                    AutoReviewJudgmentDto.of(1, Verdict.FAIL, "이전, 다음 필드가 없다", fromLabel)
            ));

            // then
            assertSoftly(softly -> {
                softly.assertThat(fromBare).containsExactly(CodeEvidenceDto.of(label, 46, 50));
                softly.assertThat(fromLabel).containsExactly(CodeEvidenceDto.of(label, 10, 12));
                softly.assertThat(merged.reason()).isEqualTo("이전, 다음 필드가 없다");
                softly.assertThat(merged.evidence()).containsExactly(CodeEvidenceDto.of(label, 10, 12));
            });
        }

        @Test
        void 근거가_없으면_빈_배열로_둔다() {
            // given
            AutoReviewResultOutputDto unknown = new AutoReviewResultOutputDto(1, null, "관련 코드가 없다", Verdict.UNKNOWN);

            // when & then
            assertThat(AutoReviewer.evidenceOf(unknown, List.of())).isEmpty();
        }
    }
}
