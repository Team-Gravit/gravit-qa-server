package gravit.qa.sprint.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.service.JobQueryService;
import gravit.qa.job.support.JobRunner;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.fixture.SprintFixture;
import gravit.qa.support.HttpStubServer;
import gravit.qa.support.HttpStubServer.RecordedRequest;
import gravit.qa.support.QaSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static gravit.qa.sprint.fixture.SetupStubFixture.C1_ROW_ID;
import static gravit.qa.sprint.fixture.SetupStubFixture.COMPLETIONS_PATH;
import static gravit.qa.sprint.fixture.SetupStubFixture.SERVER_CONTROLLER;
import static gravit.qa.sprint.fixture.SetupStubFixture.HISTORY_ROW_ID;
import static gravit.qa.sprint.fixture.SetupStubFixture.HISTORY_TABLE_ID;
import static gravit.qa.sprint.fixture.SetupStubFixture.LLM_응답;
import static gravit.qa.sprint.fixture.SetupStubFixture.LRN_PAGE_ID;
import static gravit.qa.sprint.fixture.SetupStubFixture.SPRINT_KEY;
import static gravit.qa.sprint.fixture.SetupStubFixture.GitHub_응답_등록;
import static gravit.qa.sprint.fixture.SetupStubFixture.디자인_출력;
import static gravit.qa.sprint.fixture.SetupStubFixture.반영_출력;
import static gravit.qa.sprint.fixture.SetupStubFixture.브랜치_없음_등록;
import static gravit.qa.sprint.fixture.SetupStubFixture.서버_판정_출력;
import static gravit.qa.sprint.fixture.SetupStubFixture.외부_응답_등록;
import static gravit.qa.sprint.fixture.SetupStubFixture.웹_판정_출력;
import static gravit.qa.sprint.fixture.SetupStubFixture.체크리스트_출력;
import static gravit.qa.sprint.fixture.SetupStubFixture.표_없는_명세_본문;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.표_행;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.행_응답;
import static gravit.qa.support.HttpStubServer.StubResponse.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
@Import(SprintFixture.class)
class PrepareFlowIntegrationTest {

    private static final String BEARER = "Bearer admin-access-token";
    private static final String NOTION_OK = "{\"object\": \"block\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SprintFixture sprintFixture;

    @Autowired
    private JobRunner jobRunner;

    @Autowired
    private JobQueryService jobQueryService;

    @Autowired
    private HttpStubServer externalApiStubServer;

    @Autowired
    private ObjectMapper objectMapper;

    private long sprintId;

    @BeforeEach
    void setUp() {
        Sprint sprint = sprintFixture.스프린트(SprintStatus.DRAFT);
        sprintId = sprint.getId();
        외부_응답_등록(externalApiStubServer);
        externalApiStubServer.stub("PATCH", "/v1/blocks/" + C1_ROW_ID, json(200, NOTION_OK));
        externalApiStubServer.stub("PATCH", "/v1/blocks/" + HISTORY_ROW_ID, json(200, NOTION_OK));
    }

    @Nested
    @DisplayName("명세 로드부터 1차 판정까지 진행할 때")
    class 전체_흐름 {

        @Test
        void 반영_승인_뒤_체크리스트_디자인_검수_1차_판정을_차례로_끝내고_MANUAL_REVIEW가_된다() throws Exception {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH,
                    LLM_응답(반영_출력()),
                    LLM_응답(체크리스트_출력()),
                    LLM_응답(디자인_출력()),
                    LLM_응답(서버_판정_출력(1)),
                    LLM_응답(웹_판정_출력(1)));

            // when
            준비_시작(0).andExpect(status().isAccepted()).andExpect(jsonPath("$.jobId").isNumber());
            jobRunner.runNext();

            // then
            스프린트_조회()
                    .andExpect(jsonPath("$.status").value("SPEC_LOAD"))
                    .andExpect(jsonPath("$.summary").value("유닛 상세 개편"))
                    .andExpect(jsonPath("$.connections", hasSize(5)))
                    .andExpect(jsonPath("$.connections[2].target").value("SERVER"))
                    .andExpect(jsonPath("$.connections[2].ref").value("qa-baseline@aaaaaaa"))
                    .andExpect(jsonPath("$.setupSteps[0].status").value("DONE"))
                    .andExpect(jsonPath("$.setupSteps[1].status").value("WAITING"))
                    .andExpect(jsonPath("$.setupSteps[1].attempt").value(1))
                    .andExpect(jsonPath("$.pendingReflectionCount").value(1));
            mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/reflections").header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].state").value("PENDING"))
                    .andExpect(jsonPath("$[0].screenId").value("LRN-02"))
                    .andExpect(jsonPath("$[0].historyRows[0].sprint").value(SPRINT_KEY))
                    .andExpect(jsonPath("$[0].after").value(containsString("유닛 카드 목록과 진행률")));

            // when
            결정("approve", 1, 0)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("APPLIED"));

            // then
            JsonNode rowPatch = objectMapper.readTree(externalApiStubServer.requests("PATCH", "/v1/blocks/" + C1_ROW_ID).getFirst().body());
            JsonNode historyPatch = objectMapper.readTree(externalApiStubServer.requests("PATCH", "/v1/blocks/" + HISTORY_ROW_ID).getFirst().body());
            assertSoftly(softly -> {
                softly.assertThat(rowPatch.at("/table_row/cells/2/0/text/content").asText()).isEqualTo("유닛 카드 목록과 진행률");
                softly.assertThat(historyPatch.at("/table_row/cells/2/0/text/content").asText()).isEqualTo("2026-10-16");
                softly.assertThat(historyPatch.at("/table_row/cells/0/0/text/content").asText()).isEqualTo(SPRINT_KEY);
            });
            스프린트_조회().andExpect(jsonPath("$.status").value("CHECKLIST"));

            // when
            jobRunner.runNext();

            // then
            스프린트_조회()
                    .andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
                    .andExpect(jsonPath("$.setupSteps[2].status").value("DONE"))
                    .andExpect(jsonPath("$.setupSteps[3].status").value("DONE"))
                    .andExpect(jsonPath("$.setupSteps[4].status").value("DONE"))
                    .andExpect(jsonPath("$.progress[0].part").value("DESIGN"))
                    .andExpect(jsonPath("$.progress[0].pass").value(1))
                    .andExpect(jsonPath("$.progress[1].part").value("WEB"))
                    .andExpect(jsonPath("$.progress[1].total").value(2))
                    .andExpect(jsonPath("$.progress[1].fail").value(1))
                    .andExpect(jsonPath("$.progress[3].part").value("SERVER"))
                    .andExpect(jsonPath("$.progress[3].pass").value(1));
            체크리스트(1)
                    .andExpect(jsonPath("$.features[0].screenId").value("LRN-02"))
                    .andExpect(jsonPath("$.features[0].changed").value(true))
                    .andExpect(jsonPath("$.features[0].completionRate").value(100))
                    .andExpect(jsonPath("$.features[0].items[0].classification").value("AUTO"))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].part").value("SERVER"))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].state").value("PASS"))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].source").value("AUTO"))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].reason").value("유닛 조회 API가 진행률을 돌려준다"))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].evidence[0].path").value("gravit-server/" + SERVER_CONTROLLER))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].evidence[0].startLine").value(9))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].evidence[0].endLine").value(12))
                    .andExpect(jsonPath("$.features[0].items[0].cells[1].state").value("FAIL"))
                    .andExpect(jsonPath("$.features[0].items[0].cells[1].editable").value(true));
            체크리스트(2)
                    .andExpect(jsonPath("$.features[0].items", hasSize(1)))
                    .andExpect(jsonPath("$.features[0].items[0].cells[1].state").value("UNCHECKED"))
                    .andExpect(jsonPath("$.features[0].completionRate").value(0));
            mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/design-review").header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.features[0].items[0].platform").value("WEB"))
                    .andExpect(jsonPath("$.features[0].items[0].state").value("PASS"))
                    .andExpect(jsonPath("$.features[0].items[0].expected").value(containsString("(LRN-02-C1)")))
                    .andExpect(jsonPath("$.features[0].items[0].specRowIds[0]").value("C1"));
        }
    }

    @Nested
    @DisplayName("준비 작업이 실패했다가 다시 시작할 때")
    class 이어서_실행 {

        @Test
        void 저장소_브랜치가_없으면_연결_단계가_실패하고_다시_시작하면_그_단계부터_시도_횟수가_늘어난다() throws Exception {
            // given
            브랜치_없음_등록(externalApiStubServer, "gravit-aos");
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, LLM_응답(반영_출력()));
            준비_시작(0);

            // when
            jobRunner.runNext();

            // then
            스프린트_조회()
                    .andExpect(jsonPath("$.status").value("SPEC_LOAD"))
                    .andExpect(jsonPath("$.latestJob.status").value("FAILED"))
                    .andExpect(jsonPath("$.setupSteps[0].status").value("FAILED"))
                    .andExpect(jsonPath("$.setupSteps[0].message").value(containsString("gravit-aos 저장소에 qa-baseline 브랜치가 없습니다")))
                    .andExpect(jsonPath("$.connections[4].target").value("APP"))
                    .andExpect(jsonPath("$.connections[4].connected").value(false))
                    .andExpect(jsonPath("$.connections[0].connected").value(true));

            // when
            GitHub_응답_등록(externalApiStubServer);
            준비_시작(버전()).andExpect(status().isAccepted());
            jobRunner.runNext();

            // then
            스프린트_조회()
                    .andExpect(jsonPath("$.setupSteps[0].status").value("DONE"))
                    .andExpect(jsonPath("$.setupSteps[0].attempt").value(2))
                    .andExpect(jsonPath("$.setupSteps[1].status").value("WAITING"))
                    .andExpect(jsonPath("$.connections[4].connected").value(true));
        }

        @Test
        void 명세_맨_위_표를_읽지_못하면_명세_수집이_실패하고_화면과_이유를_보여준다() throws Exception {
            // given
            externalApiStubServer.stub("GET", "/v1/blocks/" + LRN_PAGE_ID + "/children", json(200, 표_없는_명세_본문("본문만 있다")));
            준비_시작(0);

            // when
            jobRunner.runNext();

            // then
            스프린트_조회()
                    .andExpect(jsonPath("$.setupSteps[0].status").value("DONE"))
                    .andExpect(jsonPath("$.setupSteps[1].status").value("FAILED"))
                    .andExpect(jsonPath("$.setupSteps[1].message").value(containsString("LRN-02: 명세 맨 위에 항목, 값 표가 없습니다")));
        }

        @Test
        void 반영_승인_대기_중에_다시_시작하면_제안을_지우고_명세_수집부터_다시_한다() throws Exception {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, LLM_응답(반영_출력()));
            준비_시작(0);
            jobRunner.runNext();

            // when
            준비_시작(버전()).andExpect(status().isAccepted());
            jobRunner.runNext();

            // then
            스프린트_조회()
                    .andExpect(jsonPath("$.setupSteps[0].attempt").value(1))
                    .andExpect(jsonPath("$.setupSteps[1].status").value("WAITING"))
                    .andExpect(jsonPath("$.setupSteps[1].attempt").value(2))
                    .andExpect(jsonPath("$.pendingReflectionCount").value(1));
            mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/reflections").header("Authorization", BEARER))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(2));
        }
    }

    @Nested
    @DisplayName("반영 제안을 결정할 때")
    class 반영_결정 {

        @BeforeEach
        void 제안까지_진행() throws Exception {
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, LLM_응답(반영_출력()));
            준비_시작(0);
            jobRunner.runNext();
        }

        @Test
        void 제안_이후_수정_이력_행이_바뀌었으면_쓰지_않고_409_REFLECTION_4091과_FAILED를_남긴다() throws Exception {
            // given
            externalApiStubServer.stub("GET", "/v1/blocks/" + HISTORY_TABLE_ID + "/children", json(200, 행_응답(
                    표_행("2222bbbb2222bbbb2222bbbb22220013", "스프린트", "수정 사항", "반영일"),
                    표_행(HISTORY_ROW_ID, SPRINT_KEY, "다른 수정 사항", "")
            )));

            // when & then
            결정("approve", 1, 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("REFLECTION_4091"));
            List<RecordedRequest> patches = externalApiStubServer.requests().stream()
                    .filter(request -> request.method().equals("PATCH"))
                    .toList();
            assertThat(patches).isEmpty();
            mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/reflections").header("Authorization", BEARER))
                    .andExpect(jsonPath("$[0].state").value("FAILED"))
                    .andExpect(jsonPath("$[0].message").value(containsString("내용이 바뀌었습니다")));
        }

        @Test
        void 거절하면_Notion에_쓰지_않고_체크리스트_생성을_시작한다() throws Exception {
            // when
            결정("reject", 1, 0)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("REJECTED"));

            // then
            스프린트_조회()
                    .andExpect(jsonPath("$.status").value("CHECKLIST"))
                    .andExpect(jsonPath("$.setupSteps[1].status").value("DONE"))
                    .andExpect(jsonPath("$.pendingReflectionCount").value(0))
                    .andExpect(jsonPath("$.latestJob.type").value("QA_PREPARE"))
                    .andExpect(jsonPath("$.latestJob.status").value("QUEUED"));
            assertThat(externalApiStubServer.requests().stream().anyMatch(request -> request.method().equals("PATCH"))).isFalse();
        }

        @Test
        void 버전이_다르면_409_GLOBAL_4091() throws Exception {
            // when & then
            결정("approve", 1, 5)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4091"));
        }
    }

    @Nested
    @DisplayName("준비를 시작할 수 없을 때")
    class 시작_불가 {

        @Test
        void 실행_중인_작업이_있으면_409_JOB_4091() throws Exception {
            // given
            준비_시작(0);

            // when & then
            준비_시작(1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("JOB_4091"));
            assertThat(jobQueryService.findLatestJob(sprintId).orElseThrow().type()).isEqualTo(JobType.SPEC_LOAD);
            assertThat(jobQueryService.findLatestJob(sprintId).orElseThrow().status()).isEqualTo(JobStatus.QUEUED);
        }

        @Test
        void 버전이_다르면_409_GLOBAL_4091() throws Exception {
            // when & then
            준비_시작(3)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4091"));
        }
    }

    private ResultActions 준비_시작(int version) throws Exception {
        return mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/setup-jobs")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": " + version + "}"));
    }

    private ResultActions 결정(
            String action,
            long reflectionId,
            int version
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/reflections/" + reflectionId + "/" + action)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": " + version + "}"));
    }

    private ResultActions 스프린트_조회() throws Exception {
        return mockMvc.perform(get("/api/v1/sprints/" + sprintId).header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    private ResultActions 체크리스트(int round) throws Exception {
        return mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/checklist").param("round", String.valueOf(round)).header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    private int 버전() throws Exception {
        String body = 스프린트_조회().andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("version").asInt();
    }
}
