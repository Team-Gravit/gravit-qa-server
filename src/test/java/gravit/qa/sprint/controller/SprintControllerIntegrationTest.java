package gravit.qa.sprint.controller;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.domain.ReviewState;
import gravit.qa.feature.domain.Feature;
import gravit.qa.feature.domain.Part;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.domain.WarningCode;
import gravit.qa.sprint.dto.internal.WarningDto;
import gravit.qa.sprint.fixture.SprintFixture;
import gravit.qa.sprint.repository.SprintRepository;
import gravit.qa.support.HttpStubServer;
import gravit.qa.support.HttpStubServer.RecordedRequest;
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

import static gravit.qa.sprint.fixture.SetupStubFixture.AOS_SHA;
import static gravit.qa.sprint.fixture.SetupStubFixture.FIGMA_URL;
import static gravit.qa.sprint.fixture.SetupStubFixture.SERVER_SHA;
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
        void Notion_행으로_DRAFT를_만들고_기간이_없어도_등록된다() throws Exception {
            // given
            externalApiStubServer.stub("GET", SPRINT_PATH, json(200, 스프린트_페이지(List.of(), FIGMA_URL)));

            // when & then
            등록(NOTION_URL)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sprintKey").value("3rd Agile"))
                    .andExpect(jsonPath("$.notionPageId").value(SPRINT_PAGE_ID))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.periodStart").value(nullValue()))
                    .andExpect(jsonPath("$.figmaFileKey").value("AbC123"))
                    .andExpect(jsonPath("$.createdBy").value(7))
                    .andExpect(jsonPath("$.guide.stage").value("PREPARE"))
                    .andExpect(jsonPath("$.guide.nextAction").value("START_SETUP"))
                    .andExpect(jsonPath("$.progress", hasSize(3)))
                    .andExpect(jsonPath("$.baseline").value(nullValue()));
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
        void 목록은_최신_생성순이고_상태로_거를_수_있다() throws Exception {
            // given
            sprintFixture.스프린트("2nd Agile", "44444444444444444444444444444444", SprintStatus.CLOSED);
            sprintFixture.스프린트(SprintStatus.DRAFT);

            // when & then
            mockMvc.perform(get(SPRINTS_PATH).header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].sprintKey").value("3rd Agile"))
                    .andExpect(jsonPath("$[0].guide.nextAction").value("START_SETUP"))
                    .andExpect(jsonPath("$[1].guide.stage").value("DONE"));

            mockMvc.perform(get(SPRINTS_PATH).param("status", "CLOSED").header("Authorization", BEARER))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].sprintKey").value("2nd Agile"));
        }

        @Test
        void 없는_스프린트는_404_SPRINT_4041() throws Exception {
            // when & then
            mockMvc.perform(get(SPRINTS_PATH + "/999").header("Authorization", BEARER))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SPRINT_4041"));
        }

        @Test
        void 진행률은_거절_항목을_빼고_파트별로_센다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.IN_PROGRESS);
            Feature feature = sprintFixture.기능("LRN-02", List.of(Part.SERVER, Part.WEB));
            ChecklistItem checked = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, ReviewState.CONFIRMED, List.of(Part.WEB));
            sprintFixture.셀_체크(checked.getId(), Part.WEB, CellState.PASS);
            sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, ReviewState.CONFIRMED, List.of(Part.SERVER, Part.WEB));
            sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, ReviewState.REJECTED, List.of(Part.WEB));

            // when & then
            mockMvc.perform(get(SPRINTS_PATH + "/" + sprint.getId()).header("Authorization", BEARER))
                    .andExpect(jsonPath("$.progress[1].part").value("WEB"))
                    .andExpect(jsonPath("$.progress[1].total").value(2))
                    .andExpect(jsonPath("$.progress[1].pass").value(1))
                    .andExpect(jsonPath("$.progress[1].unchecked").value(1))
                    .andExpect(jsonPath("$.progress[0].unchecked").value(1))
                    .andExpect(jsonPath("$.progress[2].notApplicable").value(2))
                    .andExpect(jsonPath("$.guide.nextAction").value("CHECK_CELLS"))
                    .andExpect(jsonPath("$.guide.blockers", hasSize(2)));
        }
    }

    @Nested
    @DisplayName("QA 준비하기를 시작할 때")
    class 준비_시작 {

        @Test
        void SETUP_작업을_큐에_넣고_스프린트를_PREPARING으로_바꾼다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.DRAFT);

            // when & then
            준비(sprint.getId())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.type").value("SETUP"))
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.steps", hasSize(8)));

            assertThat(sprintRepository.findById(sprint.getId()).orElseThrow().getStatus()).isEqualTo(SprintStatus.PREPARING);
        }

        @Test
        void 준비_중이면_409_SPRINT_4092() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.DRAFT);
            준비(sprint.getId());

            // when & then
            준비(sprint.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
        }

        @Test
        void 확정_이후에는_다시_준비할_수_없어_409_SPRINT_4092() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.IN_PROGRESS);

            // when & then
            준비(sprint.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
        }

        private ResultActions 준비(long sprintId) throws Exception {
            return mockMvc.perform(post(SPRINTS_PATH + "/" + sprintId + "/setup-jobs").header("Authorization", BEARER));
        }
    }

    @Nested
    @DisplayName("스프린트를 종료할 때")
    class 종료 {

        @Test
        void 미검수_셀이_없으면_CLOSED가_되고_Notion_QA_기준점에_SHA_요약을_쓴다() throws Exception {
            // given
            Sprint sprint = 검수_끝난_스프린트();
            externalApiStubServer.stub("GET", SPRINT_PATH, json(200, 스프린트_페이지(List.of(), FIGMA_URL)));
            externalApiStubServer.stub("PATCH", SPRINT_PATH, json(200, 스프린트_페이지(List.of(), FIGMA_URL)));

            // when & then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.closedAt").isNotEmpty())
                    .andExpect(jsonPath("$.guide.stage").value("DONE"));

            RecordedRequest patch = externalApiStubServer.requests("PATCH", SPRINT_PATH).getFirst();
            assertThat(patch.body()).contains("QA 기준점", "gravit-server release " + SERVER_SHA, "gravit-aos release " + AOS_SHA + " (versionCode 1, versionName 1.0.1)");
            assertThat(sprintRepository.findById(sprint.getId()).orElseThrow().getWarnings()).isEmpty();
        }

        @Test
        void Notion_기록이_실패해도_CLOSED가_되고_NOTION_WRITE_FAILED_경고를_남긴다() throws Exception {
            // given
            Sprint sprint = 검수_끝난_스프린트();
            externalApiStubServer.stub("GET", SPRINT_PATH, StubResponse.status(404));

            // when & then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));

            assertThat(sprintRepository.findById(sprint.getId()).orElseThrow().getWarnings())
                    .extracting(WarningDto::code)
                    .containsExactly(WarningCode.NOTION_WRITE_FAILED);
        }

        @Test
        void 미검수_셀이_남아_있으면_409_SPRINT_4093() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.IN_PROGRESS);
            Feature feature = sprintFixture.기능("LRN-02", List.of(Part.WEB));
            sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, ReviewState.CONFIRMED, List.of(Part.WEB));

            // when & then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4093"));
        }

        @Test
        void 거절_항목의_미검수_셀은_종료를_막지_않는다() throws Exception {
            // given
            Sprint sprint = 검수_끝난_스프린트();
            Feature feature = sprintFixture.기능("MY-04", List.of(Part.WEB));
            sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, ReviewState.REJECTED, List.of(Part.WEB));

            // when & then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isOk());
        }

        @Test
        void 실행_중인_자동_실행이_있으면_409_SPRINT_4094() throws Exception {
            // given
            Sprint sprint = 검수_끝난_스프린트();
            jobCommandService.enqueue(JobType.AUTO_RUN, sprint.getId(), Map.of());

            // when & then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4094"));
        }

        @Test
        void version이_다르면_409_GLOBAL_4091() throws Exception {
            // given
            Sprint sprint = 검수_끝난_스프린트();

            // when & then
            종료(sprint.getId(), sprint.getVersion() + 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4091"));
        }

        @Test
        void 진행_중이_아니면_409_SPRINT_4092() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.REVIEW);

            // when & then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
        }

        private Sprint 검수_끝난_스프린트() {
            Sprint sprint = sprintFixture.스프린트(SprintStatus.IN_PROGRESS);
            sprintFixture.기준점(sprint.getId());
            Feature feature = sprintFixture.기능("LRN-02", List.of(Part.WEB));
            ChecklistItem item = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, ReviewState.CONFIRMED, List.of(Part.WEB));
            sprintFixture.셀_체크(item.getId(), Part.WEB, CellState.FAIL);
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
