package gravit.qa.job;

import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.global.util.TimeZoneConst;
import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobStepStatus;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.dto.response.JobResponse;
import gravit.qa.job.dto.response.JobStepResponse;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.job.service.JobQueryService;
import gravit.qa.job.support.JobContext;
import gravit.qa.job.support.JobHandler;
import gravit.qa.job.support.JobRunner;
import gravit.qa.job.support.NonRetryableJobException;
import gravit.qa.sprint.handler.SetupJobHandler;
import gravit.qa.support.QaSpringBootTest;
import gravit.qa.support.TestClock;
import gravit.qa.support.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Consumer;

import static gravit.qa.global.exception.domain.CustomErrorCode.JOB_ALREADY_ACTIVE;
import static gravit.qa.global.exception.domain.CustomErrorCode.JOB_NOT_CANCELLABLE;
import static gravit.qa.global.exception.domain.CustomErrorCode.JOB_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@QaSpringBootTest
@Import(JobQueueIntegrationTest.StubHandlerConfig.class)
class JobQueueIntegrationTest {

    @Autowired
    private JobCommandService jobCommandService;

    @Autowired
    private JobQueryService jobQueryService;

    @Autowired
    private JobRunner jobRunner;

    @Autowired
    private StubJobHandler stubJobHandler;

    @MockitoSpyBean
    private SetupJobHandler setupJobHandler;

    @Autowired
    private TestClock testClock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        stubJobHandler.behave(context -> context.step("COLLECT", () -> { }));
    }

    @Nested
    @DisplayName("작업을 등록할 때")
    class 등록 {

        @Test
        void 대기_상태와_유형별_단계가_생성된다() {
            // when
            long jobId = jobCommandService.enqueue(JobType.SETUP, null, Map.of());

            // then
            JobResponse job = jobQueryService.getJob(jobId);
            assertSoftly(softly -> {
                softly.assertThat(job.status()).isEqualTo(JobStatus.QUEUED);
                softly.assertThat(job.attempt()).isZero();
                softly.assertThat(job.maxAttempts()).isEqualTo(4);
                softly.assertThat(job.steps()).extracting(JobStepResponse::name)
                        .containsExactly("BASELINE", "NOTION", "FIGMA", "DIFF", "OPENAPI", "INTERPRET", "VALIDATE", "APPLY");
                softly.assertThat(job.steps()).extracting(JobStepResponse::status)
                        .containsOnly(JobStepStatus.PENDING);
            });
        }

        @Test
        void 같은_스프린트에_실행_중인_SETUP이_있으면_JOB_ALREADY_ACTIVE() {
            // given
            long sprintId = 스프린트_생성();
            jobCommandService.enqueue(JobType.SETUP, sprintId, Map.of());

            // when & then
            assertThatThrownBy(() -> jobCommandService.enqueue(JobType.SETUP, sprintId, Map.of()))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(JOB_ALREADY_ACTIVE);
        }

        @Test
        void 리뷰_수집은_같은_스프린트에_여러_개_등록할_수_있다() {
            // given
            long sprintId = 스프린트_생성();
            jobCommandService.enqueue(JobType.REVIEW_COLLECT, sprintId, Map.of());

            // when
            long secondId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, sprintId, Map.of());

            // then
            assertThat(jobQueryService.getJob(secondId).status()).isEqualTo(JobStatus.QUEUED);
        }
    }

    @Nested
    @DisplayName("작업을 실행할 때")
    class 실행 {

        @Test
        void 성공하면_SUCCEEDED가_되고_단계가_DONE이_된다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, null, Map.of());

            // when
            boolean ran = jobRunner.runNext();

            // then
            JobResponse job = jobQueryService.getJob(jobId);
            assertSoftly(softly -> {
                softly.assertThat(ran).isTrue();
                softly.assertThat(job.status()).isEqualTo(JobStatus.SUCCEEDED);
                softly.assertThat(job.attempt()).isEqualTo(1);
                softly.assertThat(job.finishedAt()).isNotNull();
                softly.assertThat(job.steps()).extracting(JobStepResponse::status).containsOnly(JobStepStatus.DONE);
            });
        }

        @Test
        void 소비_대상이_아닌_유형은_가져가지_않는다() {
            // given
            jobCommandService.enqueue(JobType.EVIDENCE_CLEANUP, null, Map.of());

            // when & then
            assertThat(jobRunner.runNext()).isFalse();
        }

        @Test
        void 재시도_가능한_실패는_대기_시간_뒤에_다시_실행된다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, null, Map.of());
            stubJobHandler.behave(context -> context.step("COLLECT", () -> {
                throw new IllegalStateException("일시 장애");
            }));

            // when
            jobRunner.runNext();

            // then
            JobResponse failed = jobQueryService.getJob(jobId);
            assertSoftly(softly -> {
                softly.assertThat(failed.status()).isEqualTo(JobStatus.QUEUED);
                softly.assertThat(failed.attempt()).isEqualTo(1);
                softly.assertThat(failed.lastError()).contains("일시 장애");
                softly.assertThat(failed.nextRunAt()).isEqualTo(기준_시각().plusMinutes(2));
                softly.assertThat(failed.steps().get(0).status()).isEqualTo(JobStepStatus.FAILED);
            });

            testClock.advance(Duration.ofMinutes(1));
            assertThat(jobRunner.runNext()).isFalse();

            stubJobHandler.behave(context -> context.step("COLLECT", () -> { }));
            testClock.advance(Duration.ofMinutes(1));
            assertThat(jobRunner.runNext()).isTrue();
            assertThat(jobQueryService.getJob(jobId).status()).isEqualTo(JobStatus.SUCCEEDED);
        }

        @Test
        void 재시도_대기는_2분_4분_8분으로_늘고_네_번째_실패에서_FAILED가_된다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, null, Map.of());
            stubJobHandler.behave(context -> {
                throw new IllegalStateException("계속 실패");
            });

            // when
            jobRunner.runNext();
            testClock.advance(Duration.ofMinutes(2));
            jobRunner.runNext();
            testClock.advance(Duration.ofMinutes(4));
            jobRunner.runNext();
            testClock.advance(Duration.ofMinutes(8));
            jobRunner.runNext();

            // then
            JobResponse job = jobQueryService.getJob(jobId);
            assertSoftly(softly -> {
                softly.assertThat(job.status()).isEqualTo(JobStatus.FAILED);
                softly.assertThat(job.attempt()).isEqualTo(4);
                softly.assertThat(job.nextRunAt()).isNull();
                softly.assertThat(job.finishedAt()).isNotNull();
            });
        }

        @Test
        void 재시도_불가_실패는_즉시_FAILED가_된다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, null, Map.of());
            stubJobHandler.behave(context -> {
                throw new NonRetryableJobException("release 브랜치 없음");
            });

            // when
            jobRunner.runNext();

            // then
            JobResponse job = jobQueryService.getJob(jobId);
            assertSoftly(softly -> {
                softly.assertThat(job.status()).isEqualTo(JobStatus.FAILED);
                softly.assertThat(job.attempt()).isEqualTo(1);
                softly.assertThat(job.lastError()).contains("release 브랜치 없음");
            });
        }

        @Test
        void 건너뛴_단계는_SKIPPED로_남는다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, null, Map.of());
            stubJobHandler.behave(context -> context.skipStep("COLLECT", "기준점 없음"));

            // when
            jobRunner.runNext();

            // then
            JobStepResponse step = jobQueryService.getJob(jobId).steps().get(0);
            assertThat(step.status()).isEqualTo(JobStepStatus.SKIPPED);
            assertThat(step.message()).isEqualTo("기준점 없음");
        }
    }

    @Nested
    @DisplayName("작업을 취소할 때")
    class 취소 {

        @Test
        void 대기_중인_작업은_즉시_CANCELLED가_되고_실행되지_않는다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, null, Map.of());

            // when
            JobResponse cancelled = jobCommandService.requestCancel(jobId);

            // then
            assertThat(cancelled.status()).isEqualTo(JobStatus.CANCELLED);
            assertThat(jobRunner.runNext()).isFalse();
        }

        @Test
        void 실행_중인_작업은_다음_단계_경계에서_CANCELLED가_된다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.SETUP, null, Map.of());
            stubSetupHandler(jobId);

            // when
            jobRunner.runNext();

            // then
            JobResponse job = jobQueryService.getJob(jobId);
            assertSoftly(softly -> {
                softly.assertThat(job.status()).isEqualTo(JobStatus.CANCELLED);
                softly.assertThat(job.cancelRequested()).isTrue();
                softly.assertThat(job.steps().get(0).status()).isEqualTo(JobStepStatus.DONE);
                softly.assertThat(job.steps().get(1).status()).isEqualTo(JobStepStatus.PENDING);
            });
        }

        @Test
        void 종료된_작업은_JOB_NOT_CANCELLABLE() {
            // given
            long jobId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, null, Map.of());
            jobRunner.runNext();

            // when & then
            assertThatThrownBy(() -> jobCommandService.requestCancel(jobId))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(JOB_NOT_CANCELLABLE);
        }

        @Test
        void 없는_작업은_JOB_NOT_FOUND() {
            // when & then
            assertThatThrownBy(() -> jobCommandService.requestCancel(999L))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(JOB_NOT_FOUND);
        }

        private void stubSetupHandler(long jobId) {
            doAnswer(invocation -> {
                JobContext context = invocation.getArgument(0);
                context.step("BASELINE", () -> jobCommandService.requestCancel(jobId));
                context.step("NOTION", () -> { });
                return null;
            }).when(setupJobHandler).handle(any());
        }
    }

    @Nested
    @DisplayName("heartbeat가 끊긴 작업을 회수할 때")
    class 회수 {

        @Test
        void 제한_시간이_지나면_다시_대기_상태가_되고_실행_중이던_단계는_FAILED가_된다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, null, Map.of());
            실행_중으로_점유(jobId);

            // when
            testClock.advance(Duration.ofMinutes(3));
            jobRunner.reapStale();

            // then
            JobResponse job = jobQueryService.getJob(jobId);
            assertSoftly(softly -> {
                softly.assertThat(job.status()).isEqualTo(JobStatus.QUEUED);
                softly.assertThat(job.lastError()).isEqualTo("heartbeat timeout");
                softly.assertThat(job.steps().get(0).status()).isEqualTo(JobStepStatus.FAILED);
            });
        }

        @Test
        void heartbeat가_갱신되면_회수하지_않는다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, null, Map.of());
            실행_중으로_점유(jobId);

            // when
            testClock.advance(Duration.ofMinutes(1));
            jobRunner.heartbeat();
            testClock.advance(Duration.ofMinutes(1).plusSeconds(30));
            jobRunner.reapStale();

            // then
            assertThat(jobQueryService.getJob(jobId).status()).isEqualTo(JobStatus.RUNNING);
        }

        private void 실행_중으로_점유(long jobId) {
            jdbcTemplate.update(
                    "UPDATE job SET status = 'RUNNING', attempt = 1, locked_by = ?, locked_at = ?, heartbeat_at = ? WHERE id = ?",
                    jobRunner.getWorkerId(),
                    기준_시각().toLocalDateTime(),
                    기준_시각().toLocalDateTime(),
                    jobId
            );
            jdbcTemplate.update("UPDATE job_step SET status = 'RUNNING' WHERE job_id = ?", jobId);
        }
    }

    private long 스프린트_생성() {
        jdbcTemplate.update("""
                INSERT INTO sprint (sprint_key, notion_page_id, status, created_by, created_at, updated_at)
                VALUES ('2026-S21', 'notion-page', 'DRAFT', 7, now(), now())
                """);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM sprint WHERE sprint_key = '2026-S21'", Long.class);
        return id == null ? -1L : id;
    }

    private OffsetDateTime 기준_시각() {
        return OffsetDateTime.ofInstant(TestClockConfig.BASE_INSTANT, TimeZoneConst.KST);
    }

    static class StubJobHandler implements JobHandler {

        private volatile Consumer<JobContext> behavior = context -> { };

        void behave(Consumer<JobContext> behavior) {
            this.behavior = behavior;
        }

        @Override
        public JobType type() {
            return JobType.REVIEW_COLLECT;
        }

        @Override
        public void handle(JobContext context) {
            behavior.accept(context);
        }
    }

    static class StubHandlerConfig {

        @Bean
        StubJobHandler stubJobHandler() {
            return new StubJobHandler();
        }
    }
}
