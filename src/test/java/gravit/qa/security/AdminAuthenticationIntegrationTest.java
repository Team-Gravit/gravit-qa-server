package gravit.qa.security;

import gravit.qa.job.domain.JobType;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.support.AdminMeStubServer;
import gravit.qa.support.QaSpringBootTest;
import gravit.qa.support.TestClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
class AdminAuthenticationIntegrationTest {

    private static final String TOKEN = "admin-access-token";
    private static final String BEARER = "Bearer " + TOKEN;

    @Autowired
    private JobCommandService jobCommandService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminMeStubServer adminMeStubServer;

    @Autowired
    private TestClock testClock;

    @Nested
    @DisplayName("관리자 판정 결과에 따라")
    class 판정_결과 {

        @Test
        void 토큰이_없으면_401_AUTH_4011을_반환하고_인증_서버를_호출하지_않는다() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/jobs/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("AUTH_4011"));

            assertThat(adminMeStubServer.hits()).isZero();
        }

        @Test
        void 인증_서버가_200이면_요청이_통과하고_토큰을_그대로_전달한다() throws Exception {
            // given
            long jobId = jobCommandService.enqueue(JobType.REVIEW_COLLECT, null, Map.of());

            // when & then
            mockMvc.perform(get("/api/v1/jobs/" + jobId).header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value((int) jobId));

            assertThat(adminMeStubServer.authorizations()).containsExactly(BEARER);
        }

        @Test
        void 인증_서버가_401이면_401_AUTH_4012를_반환한다() throws Exception {
            // given
            adminMeStubServer.respond(401, "{}");

            // when & then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("AUTH_4012"));
        }

        @Test
        void 인증_서버가_403이면_403_AUTH_4031을_반환한다() throws Exception {
            // given
            adminMeStubServer.respond(403, "{}");

            // when & then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("AUTH_4031"));
        }

        @Test
        void 인증_서버가_그_외_상태를_반환하면_503_AUTH_5031을_반환한다() throws Exception {
            // given
            adminMeStubServer.respond(500, "{}");

            // when & then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("AUTH_5031"));
        }

        @Test
        void 인증_서버가_제한_시간_안에_응답하지_않으면_503_AUTH_5031을_반환한다() throws Exception {
            // given
            adminMeStubServer.delay(Duration.ofSeconds(3));

            // when & then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("AUTH_5031"));
        }

        @Test
        void 성공_응답에_adminId가_없으면_503_AUTH_5031을_반환한다() throws Exception {
            // given
            adminMeStubServer.respond(200, "{\"nickname\": \"운영자\"}");

            // when & then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("AUTH_5031"));
        }
    }

    @Nested
    @DisplayName("판정 결과를 캐시할 때")
    class 캐시 {

        @Test
        void 성공_판정은_TTL_동안_재사용하고_만료_후_다시_확인한다() throws Exception {
            // given
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));

            // when
            testClock.advance(Duration.ofSeconds(299));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));
            int hitsWithinTtl = adminMeStubServer.hits();

            testClock.advance(Duration.ofSeconds(2));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));

            // then
            assertThat(hitsWithinTtl).isEqualTo(1);
            assertThat(adminMeStubServer.hits()).isEqualTo(2);
        }

        @Test
        void 실패_판정은_캐시하지_않는다() throws Exception {
            // given
            adminMeStubServer.respond(401, "{}");
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));

            // when
            adminMeStubServer.respond(200, AdminMeStubServer.ADMIN_BODY);
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("JOB_4041"));

            // then
            assertThat(adminMeStubServer.hits()).isEqualTo(2);
        }
    }

    @Test
    void 헬스_체크는_인증_없이_200을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        assertThat(adminMeStubServer.hits()).isZero();
    }
}
