package gravit.qa.job.support;

import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.job.service.JobQueryService;
import gravit.qa.sprint.handler.QaPrepareJobHandler;
import gravit.qa.sprint.handler.SpecLoadJobHandler;
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
    private SpecLoadJobHandler specLoadJobHandler;

    @MockitoSpyBean
    private QaPrepareJobHandler qaPrepareJobHandler;

    @BeforeEach
    void setUp() {
        doNothing().when(specLoadJobHandler).handle(any());
        doNothing().when(qaPrepareJobHandler).handle(any());
    }

    @Nested
    @DisplayName("api 프로필 워커가 작업을 집을 때")
    class 작업_점유 {

        @Test
        void 명세_로드_작업을_집어_실행한다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.SPEC_LOAD, null, Map.of());

            // when
            boolean ran = jobRunner.runNext();

            // then
            assertThat(ran).isTrue();
            assertThat(jobQueryService.getJob(jobId).status()).isEqualTo(JobStatus.SUCCEEDED);
            verify(specLoadJobHandler).handle(any());
        }

        @Test
        void QA_준비_작업을_집어_실행한다() {
            // given
            long jobId = jobCommandService.enqueue(JobType.QA_PREPARE, null, Map.of());

            // when
            boolean ran = jobRunner.runNext();

            // then
            assertThat(ran).isTrue();
            assertSoftly(softly -> {
                softly.assertThat(jobQueryService.getJob(jobId).status()).isEqualTo(JobStatus.SUCCEEDED);
                softly.assertThat(jobQueryService.getJob(jobId).attempt()).isEqualTo(1);
            });
            verify(qaPrepareJobHandler).handle(any());
        }
    }
}
