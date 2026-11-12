package gravit.qa.sprint.controller;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.feature.domain.Feature;
import gravit.qa.feature.domain.Part;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.fixture.SprintFixture;
import gravit.qa.sprint.repository.SprintRepository;
import gravit.qa.support.HttpStubServer;
import gravit.qa.support.HttpStubServer.StubResponse;
import gravit.qa.support.QaSpringBootTest;
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
import java.util.Map;

import static gravit.qa.sprint.fixture.SetupStubFixture.FIGMA_URL;
import static gravit.qa.sprint.fixture.SetupStubFixture.SPRINT_PAGE_ID;
import static gravit.qa.sprint.fixture.SetupStubFixture.SPRINT_PATH;
import static gravit.qa.sprint.fixture.SetupStubFixture.다른_DB_페이지;
import static gravit.qa.sprint.fixture.SetupStubFixture.스프린트_페이지;
import static gravit.qa.support.HttpStubServer.StubResponse.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
@Import(SprintFixture.class)
class SprintControllerIntegrationTest {

    private static final String BEARER = "Bearer admin-access-token";
    private static final String SPRINTS_PATH = "/api/v1/sprints";
    private static final String NOTION_URL = "https://www.notion.so/3rd-Agile-" + SPRINT_PAGE_ID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SprintFixture sprintFixture;

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private JobCommandService jobCommandService;

    @Autowired
    private HttpStubServer externalApiStubServer;

    @Nested
    @DisplayName("스프린트를 등록할 때")
    class 등록 {

        @Test
        void Notion_행으로_DRAFT를_만들고_변경_요약을_summary로_내려준다() throws Exception {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, json(200, 스프린트_페이지(List.of(), FIGMA_URL)));

            // when & then
            등록(NOTION_URL)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sprintKey").value("3rd Agile"))
                    .andExpect(jsonPath("$.summary").value("유닛 상세 개편"))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.version").value(0))
                    .andExpect(jsonPath("$.connections", hasSize(0)))
                    .andExpect(jsonPath("$.progress", hasSize(4)))
                    .andExpect(jsonPath("$.progress[0].part").value("DESIGN"))
                    .andExpect(jsonPath("$.setupSteps", hasSize(5)))
                    .andExpect(jsonPath("$.setupSteps[0].key").value("CONNECT"))
                    .andExpect(jsonPath("$.setupSteps[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.setupSteps[0].attempt").value(0))
                    .andExpect(jsonPath("$.latestJob").value(nullValue()))
                    .andExpect(jsonPath("$.pendingReflectionCount").value(0));
        }

        @Test
        void 같은_Notion_페이지를_다시_등록하면_409_SPRINT_4091() throws Exception {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, json(200, 스프린트_페이지(List.of(), FIGMA_URL)));
            등록(NOTION_URL);

            // when & then
            등록(NOTION_URL)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4091"));
        }

        @Test
        void 같은_스프린트_이름이_이미_있으면_409_SPRINT_4091() throws Exception {
            // given
            sprintFixture.스프린트("3rd Agile", "55555555555555555555555555555555", SprintStatus.CLOSED);
            externalApiStubServer.stub("GET", SPRINT_PATH, json(200, 스프린트_페이지(List.of(), FIGMA_URL)));

            // when & then
            등록(NOTION_URL)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4091"));
        }

        @Test
        void Notion_페이지가_없으면_404_SPRINT_4042() throws Exception {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, StubResponse.status(404));

            // when & then
            등록(NOTION_URL)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SPRINT_4042"));
        }

        @Test
        void 스프린트_DB의_행이_아니면_400_SPRINT_4001() throws Exception {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, json(200, 다른_DB_페이지()));

            // when & then
            등록(NOTION_URL)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SPRINT_4001"));
        }

        @Test
        void Notion_서버_오류가_계속되면_502_SPRINT_5021() throws Exception {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, StubResponse.status(500));

            // when & then
            등록(NOTION_URL)
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error").value("SPRINT_5021"));
        }

        @Test
        void URL이_비어_있으면_400_GLOBAL_4001() throws Exception {
            // when & then
            등록("")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4001"));
        }

        private ResultActions 등록(String notionPageUrl) throws Exception {
            return mockMvc.perform(post(SPRINTS_PATH)
                    .header("Authorization", BEARER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"notionPageUrl\": \"" + notionPageUrl + "\"}"));
        }
    }

    @Nested
    @DisplayName("스프린트를 조회할 때")
    class 조회 {

        @Test
        void 목록은_최신_생성순이고_id_이름_요약_상태만_내려준다() throws Exception {
            // given
            sprintFixture.스프린트("2nd Agile", "55555555555555555555555555555555", SprintStatus.CLOSED);
            sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);

            // when & then
            mockMvc.perform(get(SPRINTS_PATH).header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].sprintKey").value("3rd Agile"))
                    .andExpect(jsonPath("$[0].summary").value("유닛 상세 개편"))
                    .andExpect(jsonPath("$[0].status").value("MANUAL_REVIEW"))
                    .andExpect(jsonPath("$[0].progress").doesNotExist())
                    .andExpect(jsonPath("$[1].sprintKey").value("2nd Agile"));
        }

        @Test
        void 상세는_디자인_항목과_1차_2차_셀을_파트별로_모아_진행도를_내려준다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = sprintFixture.기능("LRN-02", List.of(Part.SERVER, Part.WEB));
            sprintFixture.대상_기능(sprint.getId(), feature.getId(), true);
            sprintFixture.연결_성공(sprint.getId());
            ChecklistItem auto = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.AUTO, List.of(Part.SERVER, Part.WEB));
            ChecklistItem manual = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, List.of(Part.WEB));
            sprintFixture.셀_판정(auto.getId(), Part.SERVER, CellState.PASS, "코드 확인");
            sprintFixture.셀_체크(manual.getId(), Part.WEB, CellState.FAIL);
            sprintFixture.디자인_항목(sprint.getId(), feature.getId(), DesignPlatform.WEB, CellState.UNCHECKED);

            // when & then
            mockMvc.perform(get(SPRINTS_PATH + "/" + sprint.getId()).header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.connections", hasSize(5)))
                    .andExpect(jsonPath("$.connections[0].target").value("NOTION"))
                    .andExpect(jsonPath("$.connections[0].ref").value(nullValue()))
                    .andExpect(jsonPath("$.connections[3].ref").value("qa-baseline@bbbbbbb"))
                    .andExpect(jsonPath("$.progress[0].part").value("DESIGN"))
                    .andExpect(jsonPath("$.progress[0].total").value(1))
                    .andExpect(jsonPath("$.progress[0].checked").value(0))
                    .andExpect(jsonPath("$.progress[1].part").value("WEB"))
                    .andExpect(jsonPath("$.progress[1].total").value(2))
                    .andExpect(jsonPath("$.progress[1].checked").value(1))
                    .andExpect(jsonPath("$.progress[1].fail").value(1))
                    .andExpect(jsonPath("$.progress[2].part").value("APP"))
                    .andExpect(jsonPath("$.progress[2].total").value(0))
                    .andExpect(jsonPath("$.progress[3].part").value("SERVER"))
                    .andExpect(jsonPath("$.progress[3].pass").value(1));
        }

        @Test
        void 없는_스프린트는_404_SPRINT_4041() throws Exception {
            // when & then
            mockMvc.perform(get(SPRINTS_PATH + "/999").header("Authorization", BEARER))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SPRINT_4041"));
        }
    }

    @Nested
    @DisplayName("스프린트를 종료할 때")
    class 종료 {

        @Test
        void 미검수가_없으면_FAIL이_있어도_CLOSED가_된다() throws Exception {
            // given
            Sprint sprint = 검수_중인_스프린트(CellState.FAIL, CellState.PASS);

            // when & then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
            assertThat(externalApiStubServer.requests()).isEmpty();
            assertThat(sprintRepository.findById(sprint.getId()).orElseThrow().getClosedAt()).isNotNull();
        }

        @Test
        void 체크리스트_셀이_남아_있으면_409_SPRINT_4093() throws Exception {
            // given
            Sprint sprint = 검수_중인_스프린트(CellState.UNCHECKED, CellState.PASS);

            // when & then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4093"));
        }

        @Test
        void 디자인_항목이_남아_있으면_409_SPRINT_4093() throws Exception {
            // given
            Sprint sprint = 검수_중인_스프린트(CellState.PASS, CellState.UNCHECKED);

            // when & then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4093"));
        }

        @Test
        void MANUAL_REVIEW가_아니면_409_SPRINT_4092() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.AUTO_REVIEW);

            // when & then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
        }

        @Test
        void 실행_중인_작업이_있으면_409_JOB_4091() throws Exception {
            // given
            Sprint sprint = 검수_중인_스프린트(CellState.PASS, CellState.PASS);
            jobCommandService.enqueue(JobType.QA_PREPARE, sprint.getId(), Map.of());

            // when & then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("JOB_4091"));
        }

        @Test
        void 버전이_다르면_409_GLOBAL_4091() throws Exception {
            // given
            Sprint sprint = 검수_중인_스프린트(CellState.PASS, CellState.PASS);

            // when & then
            종료(sprint.getId(), sprint.getVersion() + 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4091"));
        }

        private Sprint 검수_중인_스프린트(
                CellState cellState,
                CellState designState
        ) {
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = sprintFixture.기능("LRN-02", List.of(Part.WEB));
            sprintFixture.대상_기능(sprint.getId(), feature.getId(), false);
            ChecklistItem item = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, List.of(Part.WEB));
            if (cellState != CellState.UNCHECKED) {
                sprintFixture.셀_체크(item.getId(), Part.WEB, cellState);
            }
            sprintFixture.디자인_항목(sprint.getId(), feature.getId(), DesignPlatform.WEB, designState);
            return sprint;
        }

        private ResultActions 종료(
                long sprintId,
                int version
        ) throws Exception {
            return mockMvc.perform(post(SPRINTS_PATH + "/" + sprintId + "/close")
                    .header("Authorization", BEARER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\": " + version + "}"));
        }
    }
}
