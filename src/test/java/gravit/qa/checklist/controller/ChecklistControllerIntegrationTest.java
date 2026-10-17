package gravit.qa.checklist.controller;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.domain.ReviewState;
import gravit.qa.checklist.fixture.TestCatalogStubConfig;
import gravit.qa.checklist.repository.CheckCellHistoryRepository;
import gravit.qa.checklist.repository.CheckCellRepository;
import gravit.qa.feature.domain.Feature;
import gravit.qa.feature.domain.Part;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.domain.WarningCode;
import gravit.qa.sprint.dto.internal.WarningDto;
import gravit.qa.sprint.fixture.SprintFixture;
import gravit.qa.sprint.repository.SprintRepository;
import gravit.qa.support.QaSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static gravit.qa.checklist.fixture.TestCatalogFixture.LRN_EMPTY_STATE_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
@Import({SprintFixture.class, TestCatalogStubConfig.class})
class ChecklistControllerIntegrationTest {

    private static final String BEARER = "Bearer admin-access-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SprintFixture sprintFixture;

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private CheckCellRepository checkCellRepository;

    @Autowired
    private CheckCellHistoryRepository checkCellHistoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("체크리스트를 조회할 때")
    class 조회 {

        @Test
        void 기능별로_항목과_파트_셀을_묶고_경고는_코드만_내려준다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.IN_PROGRESS);
            Feature feature = sprintFixture.기능("LRN-02", List.of(Part.SERVER, Part.WEB));
            sprintFixture.대상_기능(sprint.getId(), feature.getId(), List.of(
                    WarningDto.of(WarningCode.FIGMA_FRAME_MISSING, "AOS 프레임 없음"),
                    WarningDto.of(WarningCode.FIGMA_FRAME_MISSING, "WEB 프레임 없음")
            ));
            sprintFixture.항목(sprint.getId(), feature.getId(), Classification.AUTO, ReviewState.CONFIRMED, List.of(Part.SERVER, Part.WEB));

            // when & then
            mockMvc.perform(get("/api/v1/sprints/" + sprint.getId() + "/checklist").header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sprintStatus").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.features[0].feature.screenId").value("LRN-02"))
                    .andExpect(jsonPath("$.features[0].feature.figmaNodeIds", contains("4:2")))
                    .andExpect(jsonPath("$.features[0].warnings", contains("FIGMA_FRAME_MISSING")))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].part").value("SERVER"))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].editable").value(false))
                    .andExpect(jsonPath("$.features[0].items[0].cells[1].editable").value(false))
                    .andExpect(jsonPath("$.features[0].items[0].cells[2].state").value("NOT_APPLICABLE"));
        }

        @Test
        void 대상_기능에서_빠진_기능이라도_항목이_남아_있으면_보여준다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.REVIEW);
            Feature removed = sprintFixture.기능("MY-04", List.of(Part.WEB));
            sprintFixture.항목(sprint.getId(), removed.getId(), Classification.MANUAL, ReviewState.CONFIRMED, List.of(Part.WEB));

            // when & then
            mockMvc.perform(get("/api/v1/sprints/" + sprint.getId() + "/checklist").header("Authorization", BEARER))
                    .andExpect(jsonPath("$.features", hasSize(1)))
                    .andExpect(jsonPath("$.features[0].feature.screenId").value("MY-04"))
                    .andExpect(jsonPath("$.features[0].items", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("항목을 추가할 때")
    class 항목_추가 {

        private Sprint sprint;
        private Feature feature;

        @BeforeEach
        void setUp() {
            sprint = sprintFixture.스프린트(SprintStatus.REVIEW);
            feature = sprintFixture.기능("LRN-02", List.of(Part.SERVER, Part.WEB));
            sprintFixture.대상_기능(sprint.getId(), feature.getId(), List.of());
        }

        @Test
        void HUMAN_CONFIRMED_항목과_파트_셀_3개를_만든다() throws Exception {
            // when & then
            추가(feature.getId(), "[\"WEB\", \"APP\"]", "MANUAL", null, "learner")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.origin").value("HUMAN"))
                    .andExpect(jsonPath("$.reviewState").value("CONFIRMED"))
                    .andExpect(jsonPath("$.parts", contains("WEB", "APP")))
                    .andExpect(jsonPath("$.criteria").value(nullValue()))
                    .andExpect(jsonPath("$.cells[0].state").value("NOT_APPLICABLE"))
                    .andExpect(jsonPath("$.cells[1].state").value("UNCHECKED"))
                    .andExpect(jsonPath("$.cells[2].state").value("UNCHECKED"));
        }

        @Test
        void AUTO는_카탈로그에_있는_testRef가_있어야_하고_없으면_422_CHECKLIST_4221() throws Exception {
            // when & then
            추가(feature.getId(), "[\"WEB\"]", "AUTO", null, null)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4221"));
            추가(feature.getId(), "[\"WEB\"]", "AUTO", LRN_EMPTY_STATE_TEST, null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.classification").value("AUTO"));
        }

        @Test
        void 모르는_페르소나_키는_422_CHECKLIST_4222() throws Exception {
            // when & then
            추가(feature.getId(), "[\"WEB\"]", "MANUAL", null, "newbie")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4222"));
        }

        @Test
        void 스프린트_대상_기능이_아니면_404_CHECKLIST_4042() throws Exception {
            // given
            Feature other = sprintFixture.기능("MY-04", List.of(Part.WEB));

            // when & then
            추가(other.getId(), "[\"WEB\"]", "MANUAL", null, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4042"));
        }

        @Test
        void REVIEW가_아니면_409_SPRINT_4092() throws Exception {
            // given
            Sprint inProgress = sprintFixture.스프린트("4th Agile", "66666666666666666666666666666666", SprintStatus.IN_PROGRESS);
            sprintFixture.대상_기능(inProgress.getId(), feature.getId(), List.of());

            // when & then
            mockMvc.perform(post("/api/v1/sprints/" + inProgress.getId() + "/checklist-items")
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(본문(feature.getId(), "[\"WEB\"]", "MANUAL", null, null)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
        }

        private ResultActions 추가(
                long featureId,
                String parts,
                String classification,
                String testRef,
                String personaKey
        ) throws Exception {
            return mockMvc.perform(post("/api/v1/sprints/" + sprint.getId() + "/checklist-items")
                    .header("Authorization", BEARER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(본문(featureId, parts, classification, testRef, personaKey)));
        }

        private String 본문(
                long featureId,
                String parts,
                String classification,
                String testRef,
                String personaKey
        ) {
            return """
                    {"featureId": %d, "title": "제목", "expected": "기대", "parts": %s, "classification": "%s", "testRef": %s, "personaKey": %s}
                    """.formatted(featureId, parts, classification, 문자열(testRef), 문자열(personaKey));
        }
    }

    @Nested
    @DisplayName("항목을 수정할 때")
    class 항목_수정 {

        private ChecklistItem item;

        @BeforeEach
        void setUp() {
            Sprint sprint = sprintFixture.스프린트(SprintStatus.REVIEW);
            Feature feature = sprintFixture.기능("LRN-02", List.of(Part.SERVER, Part.WEB));
            item = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, ReviewState.PROPOSED, List.of(Part.WEB));
            jdbcTemplate.update("UPDATE checklist_item SET persona_key = 'learner' WHERE id = ?", item.getId());
        }

        @Test
        void 파트를_바꾸면_빠진_셀은_NOT_APPLICABLE_들어온_셀은_UNCHECKED가_되고_이력을_남긴다() throws Exception {
            // when & then
            수정("{\"parts\": [\"SERVER\"], \"version\": 0}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parts", contains("SERVER")))
                    .andExpect(jsonPath("$.cells[0].state").value("UNCHECKED"))
                    .andExpect(jsonPath("$.cells[1].state").value("NOT_APPLICABLE"))
                    .andExpect(jsonPath("$.version").value(1));

            assertThat(checkCellHistoryRepository.findAll()).hasSize(2);
        }

        @Test
        void 보낸_필드만_바꾸고_null은_해제_없는_필드는_유지한다() throws Exception {
            // when & then
            수정("{\"title\": \"새 제목\", \"testRef\": null, \"reviewState\": \"CONFIRMED\", \"version\": 0}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("새 제목"))
                    .andExpect(jsonPath("$.personaKey").value("learner"))
                    .andExpect(jsonPath("$.reviewState").value("CONFIRMED"));

            수정("{\"personaKey\": null, \"version\": 1}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.personaKey").value(nullValue()))
                    .andExpect(jsonPath("$.title").value("새 제목"));
        }

        @Test
        void AUTO로_바꾸려면_카탈로그_testRef가_있어야_하고_없으면_422_CHECKLIST_4221() throws Exception {
            // when & then
            수정("{\"classification\": \"AUTO\", \"version\": 0}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4221"));
            수정("{\"classification\": \"AUTO\", \"testRef\": \"" + LRN_EMPTY_STATE_TEST + "\", \"version\": 0}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.classification").value("AUTO"))
                    .andExpect(jsonPath("$.automationGap").value(false));
        }

        @Test
        void version이_다르면_409_GLOBAL_4091() throws Exception {
            // when & then
            수정("{\"title\": \"새 제목\", \"version\": 5}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4091"));
        }

        @Test
        void PROPOSED로_되돌리면_400_GLOBAL_4001() throws Exception {
            // when & then
            수정("{\"reviewState\": \"PROPOSED\", \"version\": 0}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4001"));
        }

        @Test
        void 없는_항목은_404_CHECKLIST_4041() throws Exception {
            // when & then
            mockMvc.perform(patch("/api/v1/checklist-items/999")
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"version\": 0}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4041"));
        }

        private ResultActions 수정(String body) throws Exception {
            return mockMvc.perform(patch("/api/v1/checklist-items/" + item.getId())
                    .header("Authorization", BEARER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
        }
    }

    @Nested
    @DisplayName("분류를 확정할 때")
    class 확정 {

        private Sprint sprint;
        private Feature feature;

        @BeforeEach
        void setUp() {
            sprint = sprintFixture.스프린트(SprintStatus.REVIEW);
            sprintFixture.기준점(sprint.getId());
            feature = sprintFixture.기능("LRN-02", List.of(Part.SERVER, Part.WEB));
        }

        @Test
        void 검토_남은_항목이_있으면_409_CHECKLIST_4091() throws Exception {
            // given
            sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, ReviewState.PROPOSED, List.of(Part.WEB));

            // when & then
            확정("{\"version\": 0}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4091"));
        }

        @Test
        void AUTO_항목이_있으면_자동_실행과_AUTO_RUN_작업을_만들고_IN_PROGRESS가_된다() throws Exception {
            // given
            sprintFixture.항목(sprint.getId(), feature.getId(), Classification.AUTO, ReviewState.CONFIRMED, List.of(Part.WEB));
            sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, ReviewState.REJECTED, List.of(Part.WEB));

            // when & then
            확정("{\"version\": 0}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sprint.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.sprint.confirmedAt").isNotEmpty())
                    .andExpect(jsonPath("$.sprint.guide.nextAction").value("WAIT_AUTO_RUN"))
                    .andExpect(jsonPath("$.autoRun.status").value("QUEUED"))
                    .andExpect(jsonPath("$.autoRun.counts").value(nullValue()));

            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM job WHERE type = 'AUTO_RUN' AND sprint_id = ?", Long.class, sprint.getId())).isEqualTo(1L);
        }

        @Test
        void startAutoRun이_false면_자동_실행을_만들지_않는다() throws Exception {
            // given
            sprintFixture.항목(sprint.getId(), feature.getId(), Classification.AUTO, ReviewState.CONFIRMED, List.of(Part.WEB));

            // when & then
            확정("{\"startAutoRun\": false, \"version\": 0}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.autoRun").value(nullValue()));

            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auto_run", Long.class)).isZero();
        }

        @Test
        void version이_다르면_409_GLOBAL_4091이고_상태가_그대로다() throws Exception {
            // when & then
            확정("{\"version\": 3}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4091"));

            assertThat(sprintRepository.findById(sprint.getId()).orElseThrow().getStatus()).isEqualTo(SprintStatus.REVIEW);
        }

        private ResultActions 확정(String body) throws Exception {
            return mockMvc.perform(post("/api/v1/sprints/" + sprint.getId() + "/checklist/confirm")
                    .header("Authorization", BEARER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
        }
    }

    @Nested
    @DisplayName("셀을 수동 체크할 때")
    class 셀_체크 {

        private Sprint sprint;
        private Feature feature;

        @BeforeEach
        void setUp() {
            sprint = sprintFixture.스프린트(SprintStatus.IN_PROGRESS);
            feature = sprintFixture.기능("LRN-02", List.of(Part.SERVER, Part.WEB, Part.APP));
        }

        @Test
        void 상태를_바꾸고_변경자와_이력을_남긴다() throws Exception {
            // given
            ChecklistItem item = 항목(Classification.MANUAL, ReviewState.CONFIRMED);

            // when & then
            체크(item.getId(), Part.WEB, "FAIL", 0)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("FAIL"))
                    .andExpect(jsonPath("$.source").value("MANUAL"))
                    .andExpect(jsonPath("$.updatedBy").value(7))
                    .andExpect(jsonPath("$.editable").value(true))
                    .andExpect(jsonPath("$.version").value(1));
            체크(item.getId(), Part.WEB, "PASS", 1)
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/checklist-items/" + item.getId() + "/cells/WEB/history").header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].fromState").value("FAIL"))
                    .andExpect(jsonPath("$[0].toState").value("PASS"))
                    .andExpect(jsonPath("$[0].changedBy").value(7))
                    .andExpect(jsonPath("$[1].fromState").value("UNCHECKED"));
        }

        @Test
        void version이_다르면_409_GLOBAL_4091이고_셀이_그대로다() throws Exception {
            // given
            ChecklistItem item = 항목(Classification.MANUAL, ReviewState.CONFIRMED);
            sprintFixture.셀_체크(item.getId(), Part.WEB, CellState.PASS);

            // when & then
            체크(item.getId(), Part.WEB, "FAIL", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4091"));

            CheckCell cell = checkCellRepository.findByItemIdAndPart(item.getId(), Part.WEB).orElseThrow();
            assertSoftly(softly -> {
                softly.assertThat(cell.getState()).isEqualTo(CellState.PASS);
                softly.assertThat(checkCellHistoryRepository.findAllByItemIdAndPartOrderByChangedAtDescIdDesc(item.getId(), Part.WEB)).isEmpty();
            });
        }

        @Test
        void AUTO_항목의_WEB_SERVER_셀은_422_CHECKLIST_4223이고_APP_셀은_바꿀_수_있다() throws Exception {
            // given
            ChecklistItem item = 항목(Classification.AUTO, ReviewState.CONFIRMED);

            // when & then
            체크(item.getId(), Part.WEB, "PASS", 0)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4223"));
            체크(item.getId(), Part.SERVER, "PASS", 0)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4223"));
            체크(item.getId(), Part.APP, "PASS", 0)
                    .andExpect(status().isOk());
        }

        @Test
        void NOT_APPLICABLE_셀은_422_CHECKLIST_4223() throws Exception {
            // given
            Feature webOnly = sprintFixture.기능("MY-04", List.of(Part.WEB));
            ChecklistItem item = sprintFixture.항목(sprint.getId(), webOnly.getId(), Classification.MANUAL, ReviewState.CONFIRMED, List.of(Part.WEB));

            // when & then
            체크(item.getId(), Part.APP, "PASS", 0)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4223"));
        }

        @Test
        void 거절된_항목의_셀은_422_CHECKLIST_4223() throws Exception {
            // given
            ChecklistItem item = 항목(Classification.MANUAL, ReviewState.REJECTED);

            // when & then
            체크(item.getId(), Part.WEB, "PASS", 0)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4223"));
        }

        @Test
        void NOT_APPLICABLE로_바꾸는_요청은_400_CHECKLIST_4001() throws Exception {
            // given
            ChecklistItem item = 항목(Classification.MANUAL, ReviewState.CONFIRMED);

            // when & then
            체크(item.getId(), Part.WEB, "NOT_APPLICABLE", 0)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4001"));
        }

        @Test
        void 진행_중이_아니면_409_SPRINT_4092() throws Exception {
            // given
            Sprint review = sprintFixture.스프린트("4th Agile", "66666666666666666666666666666666", SprintStatus.REVIEW);
            ChecklistItem item = sprintFixture.항목(review.getId(), feature.getId(), Classification.MANUAL, ReviewState.CONFIRMED, List.of(Part.WEB));

            // when & then
            체크(item.getId(), Part.WEB, "PASS", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
        }

        @Test
        void 없는_셀의_이력은_404_CHECKLIST_4043() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/checklist-items/999/cells/WEB/history").header("Authorization", BEARER))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4043"));
        }

        private ChecklistItem 항목(
                Classification classification,
                ReviewState reviewState
        ) {
            return sprintFixture.항목(sprint.getId(), feature.getId(), classification, reviewState, List.of(Part.SERVER, Part.WEB, Part.APP));
        }

        private ResultActions 체크(
                long itemId,
                Part part,
                String state,
                int version
        ) throws Exception {
            return mockMvc.perform(put("/api/v1/checklist-items/" + itemId + "/cells/" + part)
                    .header("Authorization", BEARER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"state\": \"" + state + "\", \"version\": " + version + "}"));
        }
    }

    private static String 문자열(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
