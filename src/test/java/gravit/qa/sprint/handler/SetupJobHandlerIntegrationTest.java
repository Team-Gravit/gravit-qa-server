package gravit.qa.sprint.handler;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.domain.ItemOrigin;
import gravit.qa.checklist.domain.ReviewState;
import gravit.qa.checklist.dto.request.ChecklistItemCreateRequest;
import gravit.qa.checklist.dto.request.ChecklistItemUpdateRequest;
import gravit.qa.checklist.dto.response.CheckCellResponse;
import gravit.qa.checklist.dto.response.ChecklistFeatureResponse;
import gravit.qa.checklist.dto.response.ChecklistItemResponse;
import gravit.qa.checklist.dto.response.ChecklistResponse;
import gravit.qa.checklist.facade.ChecklistFacade;
import gravit.qa.checklist.fixture.TestCatalogStubConfig;
import gravit.qa.checklist.repository.ChecklistItemRepository;
import gravit.qa.feature.domain.Part;
import gravit.qa.global.util.TimeZoneConst;
import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobStepStatus;
import gravit.qa.job.dto.response.JobResponse;
import gravit.qa.job.dto.response.JobStepResponse;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.job.service.JobQueryService;
import gravit.qa.job.support.JobRunner;
import gravit.qa.sprint.domain.Baseline;
import gravit.qa.sprint.domain.GuideAction;
import gravit.qa.sprint.domain.GuideBlockerCode;
import gravit.qa.sprint.domain.PrepareFailureReason;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.response.BaselineRepoResponse;
import gravit.qa.sprint.dto.response.GuideBlockerResponse;
import gravit.qa.sprint.dto.response.SprintResponse;
import gravit.qa.sprint.facade.SetupFacade;
import gravit.qa.sprint.facade.SprintFacade;
import gravit.qa.sprint.fixture.SprintFixture;
import gravit.qa.sprint.repository.SprintRepository;
import gravit.qa.support.HttpStubServer;
import gravit.qa.support.QaSpringBootTest;
import gravit.qa.support.TestClock;
import gravit.qa.support.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static gravit.qa.checklist.fixture.TestCatalogFixture.LRN_EMPTY_STATE_TEST;
import static gravit.qa.sprint.fixture.SetupStubFixture.AOS_SHA;
import static gravit.qa.sprint.fixture.SetupStubFixture.COMPLETIONS_PATH;
import static gravit.qa.sprint.fixture.SetupStubFixture.FIGMA_URL;
import static gravit.qa.sprint.fixture.SetupStubFixture.LLM_응답;
import static gravit.qa.sprint.fixture.SetupStubFixture.LRN_PAGE_ID;
import static gravit.qa.sprint.fixture.SetupStubFixture.MY_PAGE_ID;
import static gravit.qa.sprint.fixture.SetupStubFixture.SERVER_SHA;
import static gravit.qa.sprint.fixture.SetupStubFixture.WEB_SHA;
import static gravit.qa.sprint.fixture.SetupStubFixture.명세_페이지;
import static gravit.qa.sprint.fixture.SetupStubFixture.비교_응답_등록;
import static gravit.qa.sprint.fixture.SetupStubFixture.스프린트_페이지;
import static gravit.qa.sprint.fixture.SetupStubFixture.외부_응답_등록;
import static gravit.qa.sprint.fixture.SetupStubFixture.초안;
import static gravit.qa.sprint.fixture.SetupStubFixture.항목;
import static gravit.qa.support.HttpStubServer.StubResponse.json;
import static gravit.qa.support.HttpStubServer.StubResponse.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
@Import({SprintFixture.class, TestCatalogStubConfig.class})
class SetupJobHandlerIntegrationTest {

    private static final String VALID_OUTPUT = 초안(
            항목("유닛 목록 표시", List.of("WEB"), true, "learner", null),
            항목("유닛 상세 API 응답", List.of("SERVER", "WEB"), false, null, null)
    );
    private static final String WEB_ONLY_OUTPUT = 초안(항목("팔로우 목록 표시", List.of("WEB"), true, null, null));
    private static final String OUT_OF_PART_OUTPUT = 초안(항목("앱 화면", List.of("APP"), true, null, null));

    @Autowired
    private SetupFacade setupFacade;

    @Autowired
    private SprintFacade sprintFacade;

    @Autowired
    private ChecklistFacade checklistFacade;

    @Autowired
    private JobCommandService jobCommandService;

    @Autowired
    private JobQueryService jobQueryService;

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private ChecklistItemRepository checklistItemRepository;

    @Autowired
    private JobRunner jobRunner;

    @Autowired
    private SprintFixture sprintFixture;

    @Autowired
    private HttpStubServer externalApiStubServer;

    @Autowired
    private TestClock testClock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long sprintId;

    @BeforeEach
    void setUp() {
        sprintId = sprintFixture.스프린트(SprintStatus.DRAFT).getId();
        외부_응답_등록(externalApiStubServer, 1);
        externalApiStubServer.stub("POST", COMPLETIONS_PATH, LLM_응답(VALID_OUTPUT), LLM_응답(WEB_ONLY_OUTPUT));
    }

    @Nested
    @DisplayName("수집과 해석이 모두 성공하면")
    class 성공 {

        @Test
        void 스프린트가_REVIEW가_되고_단계가_DIFF만_건너뛰고_모두_DONE이다() {
            // given
            long jobId = setupFacade.startSetup(sprintId).id();

            // when
            jobRunner.runNext();

            // then
            JobResponse job = jobQueryService.getJob(jobId);
            SprintResponse sprint = sprintFacade.getSprint(sprintId);
            assertSoftly(softly -> {
                softly.assertThat(job.status()).isEqualTo(JobStatus.SUCCEEDED);
                softly.assertThat(job.steps())
                        .extracting(JobStepResponse::name, JobStepResponse::status)
                        .containsExactly(
                                tuple("BASELINE", JobStepStatus.DONE),
                                tuple("NOTION", JobStepStatus.DONE),
                                tuple("FIGMA", JobStepStatus.DONE),
                                tuple("DIFF", JobStepStatus.SKIPPED),
                                tuple("OPENAPI", JobStepStatus.DONE),
                                tuple("INTERPRET", JobStepStatus.DONE),
                                tuple("VALIDATE", JobStepStatus.DONE),
                                tuple("APPLY", JobStepStatus.DONE)
                        );
                softly.assertThat(job.steps().get(3).message()).isEqualTo("직전 기준점 없음 - 전체 범위");
                softly.assertThat(sprint.status()).isEqualTo(SprintStatus.REVIEW);
                softly.assertThat(sprint.preparedAt()).isNotNull();
                softly.assertThat(sprint.latestSetupJobId()).isEqualTo(jobId);
                softly.assertThat(sprint.guide().nextAction()).isEqualTo(GuideAction.REVIEW_ITEMS);
                softly.assertThat(sprint.guide().blockers())
                        .containsExactly(GuideBlockerResponse.of(GuideBlockerCode.PROPOSED_ITEMS_REMAINING, 3L, null));
            });
        }

        @Test
        void 저장소_3개의_release_SHA와_AOS_버전으로_기준점을_만든다() {
            // given
            setupFacade.startSetup(sprintId);

            // when
            jobRunner.runNext();

            // then
            SprintResponse sprint = sprintFacade.getSprint(sprintId);
            assertSoftly(softly -> {
                softly.assertThat(sprint.baseline().previousBaselineId()).isNull();
                softly.assertThat(sprint.baseline().repos())
                        .extracting(BaselineRepoResponse::repo, BaselineRepoResponse::commitSha, BaselineRepoResponse::previousCommitSha, BaselineRepoResponse::aosVersionCode)
                        .containsExactly(
                                tuple("gravit-aos", AOS_SHA, null, 1),
                                tuple("gravit-server", SERVER_SHA, null, null),
                                tuple("gravit-web", WEB_SHA, null, null)
                        );
            });
        }

        @Test
        void 기능별로_항목과_셀을_만들고_항목_파트_밖의_셀은_NOT_APPLICABLE이다() {
            // given
            setupFacade.startSetup(sprintId);

            // when
            jobRunner.runNext();

            // then
            ChecklistResponse checklist = checklistFacade.getChecklist(sprintId);
            ChecklistFeatureResponse lrn = 기능(checklist, "LRN-02");
            ChecklistItemResponse listItem = lrn.items().getFirst();
            assertSoftly(softly -> {
                softly.assertThat(checklist.features()).extracting(feature -> feature.feature().screenId()).containsExactly("LRN-02", "MY-04");
                softly.assertThat(lrn.feature().figmaNodeIds()).containsExactly("4:2", "4:5");
                softly.assertThat(lrn.items()).extracting(ChecklistItemResponse::title).containsExactly("유닛 목록 표시", "유닛 상세 API 응답");
                softly.assertThat(listItem.origin()).isEqualTo(ItemOrigin.LLM);
                softly.assertThat(listItem.reviewState()).isEqualTo(ReviewState.PROPOSED);
                softly.assertThat(listItem.personaKey()).isEqualTo("learner");
                softly.assertThat(listItem.sourceRefs()).isNotEmpty();
                softly.assertThat(listItem.cells())
                        .extracting(CheckCellResponse::part, CheckCellResponse::state, CheckCellResponse::editable)
                        .containsExactly(
                                tuple(Part.SERVER, CellState.NOT_APPLICABLE, false),
                                tuple(Part.WEB, CellState.UNCHECKED, false),
                                tuple(Part.APP, CellState.NOT_APPLICABLE, false)
                        );
            });
        }

        @Test
        void 기준이_모두_충족되어도_카탈로그_테스트가_없으면_MANUAL이고_자동화_후보다() {
            // given
            setupFacade.startSetup(sprintId);

            // when
            jobRunner.runNext();

            // then
            List<ChecklistItemResponse> items = 기능(checklistFacade.getChecklist(sprintId), "LRN-02").items();
            assertThat(items)
                    .extracting(ChecklistItemResponse::classification, ChecklistItemResponse::automationGap)
                    .containsExactly(
                            tuple(Classification.MANUAL, true),
                            tuple(Classification.MANUAL, false)
                    );
        }

        @Test
        void 확인_필요_명세와_프레임_없는_기능은_기대_결과에_근거_없음을_붙이고_경고를_남긴다() {
            // given
            setupFacade.startSetup(sprintId);

            // when
            jobRunner.runNext();

            // then
            ChecklistResponse checklist = checklistFacade.getChecklist(sprintId);
            ChecklistFeatureResponse my = 기능(checklist, "MY-04");
            assertSoftly(softly -> {
                softly.assertThat(my.warnings()).containsExactly("SPEC_NEEDS_CHECK", "FIGMA_FRAME_MISSING");
                softly.assertThat(my.items().getFirst().expected())
                        .startsWith("근거 없음(명세 확인 필요) - 근거 없음(Figma WEB 프레임 없음) - ");
                softly.assertThat(기능(checklist, "LRN-02").warnings()).containsExactly("OPENAPI_OPERATION_MISSING");
            });
        }

        @Test
        void 수집_원본을_스냅샷으로_남기고_대상_기능_프레임만_PNG로_저장한다() {
            // given
            long jobId = setupFacade.startSetup(sprintId).id();

            // when
            jobRunner.runNext();

            // then
            Map<String, Long> sourceToCount = jdbcTemplate.queryForList("SELECT source, COUNT(*) AS count FROM snapshot WHERE job_id = ? GROUP BY source", jobId).stream()
                    .collect(Collectors.toMap(row -> (String) row.get("source"), row -> (Long) row.get("count")));
            List<String> framePaths = jdbcTemplate.queryForList("SELECT storage_path FROM snapshot WHERE source = 'FIGMA_FRAME' ORDER BY storage_path", String.class);
            assertSoftly(softly -> {
                softly.assertThat(sourceToCount).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "NOTION_SPRINT", 1L,
                        "NOTION_SPEC", 2L,
                        "FIGMA_FRAME", 2L,
                        "OPENAPI", 1L
                ));
                softly.assertThat(framePaths).containsExactly(
                        "snapshots/" + sprintId + "/" + jobId + "/FIGMA_FRAME/WEB_4-2.png",
                        "snapshots/" + sprintId + "/" + jobId + "/FIGMA_FRAME/WEB_4-5.png"
                );
            });
        }
    }

    @Nested
    @DisplayName("직전에 종료된 스프린트가 있으면")
    class 직전_기준점 {

        private Baseline previous;

        @BeforeEach
        void setUp() {
            Sprint closed = sprintFixture.스프린트("2nd Agile", "44444444444444444444444444444444", SprintStatus.CLOSED);
            previous = sprintFixture.기준점(closed.getId(), "d".repeat(40), "e".repeat(40), "f".repeat(40), 1);
            비교_응답_등록(externalApiStubServer, "gravit-server", "d".repeat(40), SERVER_SHA, "src/main/java/Unit.java");
            비교_응답_등록(externalApiStubServer, "gravit-web", "e".repeat(40), WEB_SHA, "apps/web/src/Unit.tsx");
            비교_응답_등록(externalApiStubServer, "gravit-aos", "f".repeat(40), AOS_SHA, "app/src/Unit.kt");
        }

        @Test
        void 직전_SHA와_비교해_변경_파일을_수집하고_LLM_입력에_경로만_넣는다() {
            // given
            long jobId = setupFacade.startSetup(sprintId).id();

            // when
            jobRunner.runNext();

            // then
            JobResponse job = jobQueryService.getJob(jobId);
            SprintResponse sprint = sprintFacade.getSprint(sprintId);
            String llmRequest = externalApiStubServer.requests("POST", COMPLETIONS_PATH).getFirst().body();
            assertSoftly(softly -> {
                softly.assertThat(job.steps().get(3).status()).isEqualTo(JobStepStatus.DONE);
                softly.assertThat(sprint.baseline().previousBaselineId()).isEqualTo(previous.getId());
                softly.assertThat(sprint.baseline().repos())
                        .extracting(BaselineRepoResponse::previousCommitSha)
                        .containsExactly("f".repeat(40), "d".repeat(40), "e".repeat(40));
                softly.assertThat(llmRequest).contains("modified src/main/java/Unit.java", "modified apps/web/src/Unit.tsx");
                softly.assertThat(llmRequest).doesNotContain("@@ -1 +1 @@");
                softly.assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM snapshot WHERE source = 'GITHUB_DIFF'", Long.class)).isEqualTo(3L);
            });
        }

        @Test
        void AOS_versionCode가_직전보다_크지_않으면_경고를_남긴다() {
            // given
            setupFacade.startSetup(sprintId);

            // when
            jobRunner.runNext();

            // then
            BaselineRepoResponse aos = sprintFacade.getSprint(sprintId).baseline().repos().getFirst();
            assertSoftly(softly -> {
                softly.assertThat(aos.repo()).isEqualTo("gravit-aos");
                softly.assertThat(aos.versionCodeWarning()).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("release 브랜치가 없으면")
    class 브랜치_없음 {

        @Test
        void 재시도_없이_실패하고_스프린트가_PREPARE_FAILED가_되어_브랜치_없음을_안내한다() {
            // given
            externalApiStubServer.stub("GET", "/repos/Team-Gravit/gravit-server/branches/release", status(404));
            long jobId = setupFacade.startSetup(sprintId).id();

            // when
            jobRunner.runNext();

            // then
            JobResponse job = jobQueryService.getJob(jobId);
            SprintResponse sprint = sprintFacade.getSprint(sprintId);
            assertSoftly(softly -> {
                softly.assertThat(job.status()).isEqualTo(JobStatus.FAILED);
                softly.assertThat(job.attempt()).isEqualTo(1);
                softly.assertThat(job.steps().getFirst().status()).isEqualTo(JobStepStatus.FAILED);
                softly.assertThat(sprint.status()).isEqualTo(SprintStatus.PREPARE_FAILED);
                softly.assertThat(sprintRepository.findById(sprintId).orElseThrow().getPrepareFailureReason())
                        .isEqualTo(PrepareFailureReason.RELEASE_BRANCH_MISSING);
                softly.assertThat(sprint.guide().nextAction()).isEqualTo(GuideAction.RETRY_SETUP);
                softly.assertThat(sprint.guide().blockers())
                        .containsExactly(GuideBlockerResponse.of(GuideBlockerCode.RELEASE_BRANCH_MISSING, null, null));
            });
        }
    }

    @Nested
    @DisplayName("LLM 출력이 계약을 지키지 않으면")
    class LLM_출력_실패 {

        @Test
        void 세_번_모두_실패한_기능만_초안_생성_실패_항목_하나로_남기고_다른_기능은_계속한다() {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH,
                    LLM_응답(OUT_OF_PART_OUTPUT), LLM_응답(OUT_OF_PART_OUTPUT), LLM_응답(OUT_OF_PART_OUTPUT), LLM_응답(WEB_ONLY_OUTPUT));
            long jobId = setupFacade.startSetup(sprintId).id();

            // when
            jobRunner.runNext();

            // then
            ChecklistResponse checklist = checklistFacade.getChecklist(sprintId);
            ChecklistFeatureResponse lrn = 기능(checklist, "LRN-02");
            assertSoftly(softly -> {
                softly.assertThat(jobQueryService.getJob(jobId).status()).isEqualTo(JobStatus.SUCCEEDED);
                softly.assertThat(lrn.items()).extracting(ChecklistItemResponse::title).containsExactly("초안 생성 실패");
                softly.assertThat(lrn.items().getFirst().parts()).containsExactly(Part.SERVER, Part.WEB);
                softly.assertThat(lrn.items().getFirst().criteria()).isNull();
                softly.assertThat(lrn.warnings()).contains("ITEM_GENERATION_FAILED");
                softly.assertThat(기능(checklist, "MY-04").items()).extracting(ChecklistItemResponse::title).containsExactly("팔로우 목록 표시");
                softly.assertThat(externalApiStubServer.requests("POST", COMPLETIONS_PATH)).hasSize(4);
            });
        }

        @Test
        void 사용_한도_429가_연속되면_작업이_재시도_없이_실패한다() {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, status(429), status(429));
            long jobId = setupFacade.startSetup(sprintId).id();

            // when
            jobRunner.runNext();

            // then
            JobResponse job = jobQueryService.getJob(jobId);
            assertSoftly(softly -> {
                softly.assertThat(job.status()).isEqualTo(JobStatus.FAILED);
                softly.assertThat(job.lastError()).contains("LlmRateLimitedException");
                softly.assertThat(sprintFacade.getSprint(sprintId).status()).isEqualTo(SprintStatus.PREPARE_FAILED);
            });
        }
    }

    @Nested
    @DisplayName("Notion 내용으로 준비할 수 없으면")
    class Notion_입력_오류 {

        @Test
        void 대상_기능이_없으면_재시도_없이_실패한다() {
            // given
            externalApiStubServer.stub("GET", "/v1/pages/11111111111111111111111111111111", json(200, 스프린트_페이지(List.of(), FIGMA_URL)));

            // when
            JobResponse job = 실행();

            // then
            assertSoftly(softly -> {
                softly.assertThat(job.status()).isEqualTo(JobStatus.FAILED);
                softly.assertThat(job.attempt()).isEqualTo(1);
                softly.assertThat(job.lastError()).contains("대상 기능 없음");
                softly.assertThat(sprintFacade.getSprint(sprintId).status()).isEqualTo(SprintStatus.PREPARE_FAILED);
            });
        }

        @Test
        void 모르는_파트_라벨이면_라벨_원문을_남기고_재시도_없이_실패한다() {
            // given
            externalApiStubServer.stub("GET", "/v1/pages/" + LRN_PAGE_ID, json(200,
                    명세_페이지(LRN_PAGE_ID, "LRN-02", List.of("모바일"), "확정", "")));

            // when
            JobResponse job = 실행();

            // then
            assertSoftly(softly -> {
                softly.assertThat(job.status()).isEqualTo(JobStatus.FAILED);
                softly.assertThat(job.lastError()).contains("'모바일'", "LRN-02");
            });
        }

        @Test
        void Figma_QA_페이지가_비어_있으면_FIGMA_단계에서_재시도_없이_실패한다() {
            // given
            externalApiStubServer.stub("GET", "/v1/pages/11111111111111111111111111111111", json(200, 스프린트_페이지(List.of(LRN_PAGE_ID, MY_PAGE_ID), null)));

            // when
            JobResponse job = 실행();

            // then
            assertSoftly(softly -> {
                softly.assertThat(job.status()).isEqualTo(JobStatus.FAILED);
                softly.assertThat(job.steps().get(2).status()).isEqualTo(JobStepStatus.FAILED);
                softly.assertThat(job.lastError()).contains("Figma QA 페이지");
            });
        }

        private JobResponse 실행() {
            long jobId = setupFacade.startSetup(sprintId).id();
            jobRunner.runNext();
            return jobQueryService.getJob(jobId);
        }
    }

    @Nested
    @DisplayName("REVIEW에서 다시 준비하면")
    class 재준비 {

        @Test
        void PROPOSED_항목만_교체하고_확정_항목과_사람이_추가한_항목은_유지한다() {
            // given
            setupFacade.startSetup(sprintId);
            jobRunner.runNext();
            List<ChecklistItem> firstItems = checklistItemRepository.findAllBySprintIdOrderByFeatureIdAscSortOrderAscIdAsc(sprintId);
            ChecklistItem confirmed = firstItems.getFirst();
            checklistFacade.updateItem(confirmed.getId(), 확정_요청(confirmed.getVersion()), SprintFixture.ADMIN_ID);
            long humanId = checklistFacade.createItem(sprintId, new ChecklistItemCreateRequest(
                    confirmed.getFeatureId(), "사람이 추가한 항목", "기대 결과", Set.of(Part.WEB), Classification.MANUAL, null, null)).id();
            Set<Long> proposedIds = firstItems.stream().skip(1).map(ChecklistItem::getId).collect(Collectors.toSet());

            // when
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, LLM_응답(VALID_OUTPUT), LLM_응답(WEB_ONLY_OUTPUT));
            setupFacade.startSetup(sprintId);
            jobRunner.runNext();

            // then
            List<ChecklistItem> items = checklistItemRepository.findAllBySprintIdOrderByFeatureIdAscSortOrderAscIdAsc(sprintId);
            Set<Long> itemIds = items.stream().map(ChecklistItem::getId).collect(Collectors.toSet());
            assertSoftly(softly -> {
                softly.assertThat(sprintFacade.getSprint(sprintId).status()).isEqualTo(SprintStatus.REVIEW);
                softly.assertThat(itemIds).contains(confirmed.getId(), humanId);
                softly.assertThat(itemIds).doesNotContainAnyElementsOf(proposedIds);
                softly.assertThat(items.stream().filter(item -> item.getReviewState() == ReviewState.PROPOSED)).hasSize(3);
                softly.assertThat(items.stream().filter(item -> item.getOrigin() == ItemOrigin.HUMAN)).hasSize(1);
            });
        }

        private ChecklistItemUpdateRequest 확정_요청(int version) {
            return new ChecklistItemUpdateRequest(null, null, null, null, JsonNullable.undefined(), JsonNullable.undefined(), ReviewState.CONFIRMED, version);
        }
    }

    @Nested
    @DisplayName("카탈로그에 대응 테스트가 있으면")
    class 자동_분류 {

        @Test
        void 기준이_모두_충족된_항목은_AUTO가_된다() {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH,
                    LLM_응답(초안(항목("빈 상태 화면", List.of("WEB"), true, "fresh", LRN_EMPTY_STATE_TEST))),
                    LLM_응답(WEB_ONLY_OUTPUT));
            setupFacade.startSetup(sprintId);

            // when
            jobRunner.runNext();

            // then
            ChecklistItemResponse item = 기능(checklistFacade.getChecklist(sprintId), "LRN-02").items().getFirst();
            assertSoftly(softly -> {
                softly.assertThat(item.classification()).isEqualTo(Classification.AUTO);
                softly.assertThat(item.automationGap()).isFalse();
                softly.assertThat(item.testRef()).isEqualTo(LRN_EMPTY_STATE_TEST);
            });
        }
    }

    @Nested
    @DisplayName("작업이 취소되거나 회수로 끝나면")
    class 상태_동기화 {

        @Test
        void 실행_중_취소되면_CANCELLED가_되고_스프린트가_PREPARE_FAILED가_된다() {
            // given
            long jobId = setupFacade.startSetup(sprintId).id();
            jdbcTemplate.update("UPDATE job SET cancel_requested = TRUE WHERE id = ?", jobId);

            // when
            jobRunner.runNext();

            // then
            assertSoftly(softly -> {
                softly.assertThat(jobQueryService.getJob(jobId).status()).isEqualTo(JobStatus.CANCELLED);
                softly.assertThat(sprintFacade.getSprint(sprintId).status()).isEqualTo(SprintStatus.PREPARE_FAILED);
            });
        }

        @Test
        void 대기_중_취소되면_즉시_스프린트가_PREPARE_FAILED가_된다() {
            // given
            long jobId = setupFacade.startSetup(sprintId).id();

            // when
            jobCommandService.requestCancel(jobId);

            // then
            assertSoftly(softly -> {
                softly.assertThat(jobQueryService.getJob(jobId).status()).isEqualTo(JobStatus.CANCELLED);
                softly.assertThat(sprintFacade.getSprint(sprintId).status()).isEqualTo(SprintStatus.PREPARE_FAILED);
                softly.assertThat(sprintFacade.getSprint(sprintId).guide().nextAction()).isEqualTo(GuideAction.RETRY_SETUP);
            });
        }

        @Test
        void heartbeat_회수로_시도가_소진되면_스프린트가_PREPARE_FAILED가_된다() {
            // given
            long jobId = setupFacade.startSetup(sprintId).id();
            LocalDateTime base = LocalDateTime.ofInstant(TestClockConfig.BASE_INSTANT, TimeZoneConst.KST);
            jdbcTemplate.update(
                    "UPDATE job SET status = 'RUNNING', attempt = max_attempts, locked_by = 'other-worker', locked_at = ?, heartbeat_at = ? WHERE id = ?",
                    base, base, jobId
            );

            // when
            testClock.advance(Duration.ofMinutes(3));
            jobRunner.reapStale();

            // then
            assertSoftly(softly -> {
                softly.assertThat(jobQueryService.getJob(jobId).status()).isEqualTo(JobStatus.FAILED);
                softly.assertThat(sprintFacade.getSprint(sprintId).status()).isEqualTo(SprintStatus.PREPARE_FAILED);
            });
        }

        @Test
        void 재시도가_남은_실패는_스프린트를_PREPARING으로_둔다() {
            // given
            externalApiStubServer.stub("GET", "/repos/Team-Gravit/gravit-server/branches/release", status(500));
            long jobId = setupFacade.startSetup(sprintId).id();

            // when
            jobRunner.runNext();

            // then
            assertSoftly(softly -> {
                softly.assertThat(jobQueryService.getJob(jobId).status()).isEqualTo(JobStatus.QUEUED);
                softly.assertThat(sprintFacade.getSprint(sprintId).status()).isEqualTo(SprintStatus.PREPARING);
            });
        }
    }

    private static ChecklistFeatureResponse 기능(
            ChecklistResponse checklist,
            String screenId
    ) {
        return checklist.features().stream()
                .filter(feature -> feature.feature().screenId().equals(screenId))
                .findFirst()
                .orElseThrow();
    }
}
