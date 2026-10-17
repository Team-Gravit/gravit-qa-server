package gravit.qa.job.support;

import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.job.service.JobQueryService;
import gravit.qa.sprint.handler.SetupJobHandler;
import gravit.qa.support.QaSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@QaSpringBootTest
@ActiveProfiles({"test", "api"})
class JobRunnerApiProfileIntegrationTest {

    @Autowired
    private JobCommandService jobCommandService;

    @Autowired
    private JobQueryService jobQueryService;

    @Autowired
    private JobRunner jobRunner;

    @MockitoSpyBean
    private SetupJobHandler setupJobHandler;

    @BeforeEach
    void setUp() {
        doNothing().when(setupJobHandler).handle(any());
    }

    @Nested
    @DisplayName("api 프로필 워커가 작업을 집을 때")
    class 작업_점유 {

        @Test
        void SETUP_작업을_집어_실행한다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.SETUP, null, Map.of());

            // when
            boolean ran = jobRunner.runNext();

            // then
            assertThat(ran).isTrue();
            assertThat(jobQueryService.getJob(jobId).status()).isEqualTo(JobStatus.SUCCEEDED);
            verify(setupJobHandler).handle(any());
        }

        @Test
        void AUTO_RUN_작업은_집지_않고_대기로_남긴다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.AUTO_RUN, null, Map.of());

            // when
            boolean ran = jobRunner.runNext();

            // then
            assertThat(ran).isFalse();
            assertSoftly(softly -> {
                softly.assertThat(jobQueryService.getJob(jobId).status()).isEqualTo(JobStatus.QUEUED);
                softly.assertThat(jobQueryService.getJob(jobId).attempt()).isZero();
            });
        }

        @Test
        void 처리기가_없는_REVIEW_COLLECT와_EVIDENCE_CLEANUP_작업은_집지_않는다() {
            // given
            long reviewJobId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, null, Map.of());
            long cleanupJobId = jobCommandService.enqueue(JobType.EVIDENCE_CLEANUP, null, Map.of());

            // when
            boolean ran = jobRunner.runNext();

            // then
            assertThat(ran).isFalse();
            assertSoftly(softly -> {
                softly.assertThat(jobQueryService.getJob(reviewJobId).status()).isEqualTo(JobStatus.QUEUED);
                softly.assertThat(jobQueryService.getJob(cleanupJobId).status()).isEqualTo(JobStatus.QUEUED);
            });
        }

        @Test
        void AUTO_RUN이_먼저_대기_중이어도_뒤의_SETUP을_집는다() {
            // given
            long autoRunJobId = jobCommandService.enqueue(JobType.AUTO_RUN, null, Map.of());
            long setupJobId = jobCommandService.enqueue(JobType.SETUP, null, Map.of());

            // when
            boolean ran = jobRunner.runNext();

            // then
            assertThat(ran).isTrue();
            assertSoftly(softly -> {
                softly.assertThat(jobQueryService.getJob(setupJobId).status()).isEqualTo(JobStatus.SUCCEEDED);
                softly.assertThat(jobQueryService.getJob(autoRunJobId).status()).isEqualTo(JobStatus.QUEUED);
            });
        }
    }
}
