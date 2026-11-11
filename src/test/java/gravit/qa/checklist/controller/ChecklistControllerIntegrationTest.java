package gravit.qa.checklist.controller;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.repository.CheckCellRepository;
import gravit.qa.checklist.repository.ChecklistItemRepository;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.domain.DesignReviewItem;
import gravit.qa.feature.domain.Feature;
import gravit.qa.feature.domain.Part;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.fixture.SprintFixture;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
@Import(SprintFixture.class)
class ChecklistControllerIntegrationTest {

    private static final String BEARER = "Bearer admin-access-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SprintFixture sprintFixture;

    @Autowired
    private ChecklistItemRepository checklistItemRepository;

    @Autowired
    private CheckCellRepository checkCellRepository;

    @Nested
    @DisplayName("체크리스트를 조회할 때")
    class 조회 {

        @Test
        void round_1은_AUTO_round_2는_MANUAL_항목을_화면별로_묶고_검수_비율을_낸다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = 기능(sprint, true);
            ChecklistItem auto = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.AUTO, List.of(Part.SERVER, Part.WEB, Part.APP));
            sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, List.of(Part.WEB));
            sprintFixture.셀_판정(auto.getId(), Part.SERVER, CellState.PASS, "코드에서 확인");
            sprintFixture.셀_판정(auto.getId(), Part.WEB, CellState.FAIL, "문구가 다르다");

            // when & then
            체크리스트(sprint.getId(), "1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sprintStatus").value("MANUAL_REVIEW"))
                    .andExpect(jsonPath("$.features", hasSize(1)))
                    .andExpect(jsonPath("$.features[0].screenId").value("LRN-02"))
                    .andExpect(jsonPath("$.features[0].screenName").value("LRN-02 화면"))
                    .andExpect(jsonPath("$.features[0].changed").value(true))
                    .andExpect(jsonPath("$.features[0].completionRate").value(66))
                    .andExpect(jsonPath("$.features[0].items", hasSize(1)))
                    .andExpect(jsonPath("$.features[0].items[0].classification").value("AUTO"))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].source").value("AUTO"))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].reason").value("코드에서 확인"))
                    .andExpect(jsonPath("$.features[0].items[0].cells[0].editable").value(true));
            체크리스트(sprint.getId(), "2")
                    .andExpect(jsonPath("$.features[0].items", hasSize(1)))
                    .andExpect(jsonPath("$.features[0].items[0].classification").value("MANUAL"))
                    .andExpect(jsonPath("$.features[0].completionRate").value(0));
        }

        @Test
        void round가_없거나_1_2가_아니면_400_GLOBAL_4001() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);

            // when & then
            mockMvc.perform(get("/api/v1/sprints/" + sprint.getId() + "/checklist").header("Authorization", BEARER))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4001"));
            체크리스트(sprint.getId(), "3")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4001"));
        }
    }

    @Nested
    @DisplayName("체크 항목을 편집할 때")
    class 항목_편집 {

        @Test
        void 체크리스트_생성_뒤에는_사람이_항목을_추가한다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.DESIGN_REVIEW);
            Feature feature = 기능(sprint, false);

            // when & then
            mockMvc.perform(post("/api/v1/sprints/" + sprint.getId() + "/checklist-items")
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"featureId": %d, "title": "로그인 제공자 버튼 순서를 표시한다",
                                     "expected": "구글, 카카오, 네이버 버튼이 그 순서로 보이는지 확인한다", "parts": ["WEB"], "classification": "AUTO"}
                                    """.formatted(feature.getId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.origin").value("HUMAN"))
                    .andExpect(jsonPath("$.cells", hasSize(3)))
                    .andExpect(jsonPath("$.cells[1].part").value("WEB"))
                    .andExpect(jsonPath("$.cells[1].state").value("UNCHECKED"))
                    .andExpect(jsonPath("$.cells[1].editable").value(false))
                    .andExpect(jsonPath("$.testRef").doesNotExist());
        }

        @Test
        void 체크리스트_생성_전에는_409_SPRINT_4092() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.CHECKLIST);
            Feature feature = 기능(sprint, false);

            // when & then
            mockMvc.perform(post("/api/v1/sprints/" + sprint.getId() + "/checklist-items")
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"featureId": %d, "title": "행위", "expected": "설명", "parts": ["WEB"], "classification": "MANUAL"}
                                    """.formatted(feature.getId())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
        }

        @Test
        void 행위를_바꾸면_1차_판정을_UNCHECKED로_되돌리고_이력에_사유를_남긴다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = 기능(sprint, false);
            ChecklistItem item = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.AUTO, List.of(Part.WEB));
            sprintFixture.셀_판정(item.getId(), Part.WEB, CellState.PASS, "코드에서 확인");

            // when
            mockMvc.perform(patch("/api/v1/checklist-items/" + item.getId())
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\": \"다른 행위\", \"version\": 0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("다른 행위"))
                    .andExpect(jsonPath("$.cells[1].state").value("UNCHECKED"))
                    .andExpect(jsonPath("$.cells[1].source").value("MANUAL"))
                    .andExpect(jsonPath("$.cells[1].reason").value("항목 수정으로 판정 초기화"));

            // then
            mockMvc.perform(get("/api/v1/checklist-items/" + item.getId() + "/cells/WEB/history").header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].fromState").value("PASS"))
                    .andExpect(jsonPath("$[0].toState").value("UNCHECKED"))
                    .andExpect(jsonPath("$[0].reason").value("항목 수정으로 판정 초기화"))
                    .andExpect(jsonPath("$[0].changedBy").value(7));
        }

        @Test
        void 분류를_바꾸면_다른_차수_체크리스트로_옮겨진다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = 기능(sprint, false);
            ChecklistItem item = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.AUTO, List.of(Part.WEB));

            // when
            mockMvc.perform(patch("/api/v1/checklist-items/" + item.getId())
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"classification\": \"MANUAL\", \"version\": 0}"))
                    .andExpect(status().isOk());

            // then
            체크리스트(sprint.getId(), "1").andExpect(jsonPath("$.features", hasSize(0)));
            체크리스트(sprint.getId(), "2").andExpect(jsonPath("$.features[0].items[0].id").value(item.getId()));
        }

        @Test
        void 항목을_지우면_204이고_셀도_함께_지워진다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = 기능(sprint, false);
            ChecklistItem item = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, List.of(Part.WEB));

            // when & then
            mockMvc.perform(delete("/api/v1/checklist-items/" + item.getId()).param("version", "0").header("Authorization", BEARER))
                    .andExpect(status().isNoContent());
            assertThat(checklistItemRepository.findById(item.getId())).isEmpty();
            assertThat(checkCellRepository.findAllByItemId(item.getId())).isEmpty();
        }

        @Test
        void 버전이_다르면_지우지_않고_409_GLOBAL_4091() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = 기능(sprint, false);
            ChecklistItem item = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, List.of(Part.WEB));

            // when & then
            mockMvc.perform(delete("/api/v1/checklist-items/" + item.getId()).param("version", "3").header("Authorization", BEARER))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4091"));
            assertThat(checklistItemRepository.findById(item.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("셀을 바꿀 때")
    class 셀_변경 {

        @Test
        void MANUAL_REVIEW에서는_1차_판정_결과도_사람이_바꾸고_사유는_비운다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = 기능(sprint, false);
            ChecklistItem item = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.AUTO, List.of(Part.WEB));
            sprintFixture.셀_판정(item.getId(), Part.WEB, CellState.FAIL, "문구가 다르다");

            // when & then
            셀_변경(item.getId(), "WEB", "PASS", 1)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("PASS"))
                    .andExpect(jsonPath("$.source").value("MANUAL"))
                    .andExpect(jsonPath("$.reason").value(nullValue()))
                    .andExpect(jsonPath("$.version").value(2));
        }

        @Test
        void MANUAL_REVIEW가_아니면_409_SPRINT_4092() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.AUTO_REVIEW);
            Feature feature = 기능(sprint, false);
            ChecklistItem item = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, List.of(Part.WEB));

            // when & then
            셀_변경(item.getId(), "WEB", "PASS", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
        }

        @Test
        void 해당_없는_셀은_422_CHECKLIST_4223() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = 기능(sprint, false);
            ChecklistItem item = sprintFixture.항목(sprint.getId(), feature.getId(), Classification.MANUAL, List.of(Part.WEB));

            // when & then
            셀_변경(item.getId(), "APP", "PASS", 0)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("CHECKLIST_4223"));
        }
    }

    @Nested
    @DisplayName("디자인 검수 항목을 바꿀 때")
    class 디자인_변경 {

        @Test
        void MANUAL_REVIEW에서_사람이_바꾸면_이력에_자동_비교와_사람_변경이_남는다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = 기능(sprint, false);
            DesignReviewItem item = sprintFixture.디자인_항목(sprint.getId(), feature.getId(), DesignPlatform.WEB, CellState.FAIL);

            // when & then
            mockMvc.perform(put("/api/v1/design-review-items/" + item.getId())
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"state\": \"PASS\", \"version\": 0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("PASS"))
                    .andExpect(jsonPath("$.source").value("MANUAL"))
                    .andExpect(jsonPath("$.reason").value(nullValue()));
            mockMvc.perform(get("/api/v1/design-review-items/" + item.getId() + "/history").header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].state").value("PASS"))
                    .andExpect(jsonPath("$[0].source").value("MANUAL"))
                    .andExpect(jsonPath("$[0].updatedBy").value(7));
        }

        @Test
        void NOT_APPLICABLE은_400_DESIGN_4001() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = 기능(sprint, false);
            DesignReviewItem item = sprintFixture.디자인_항목(sprint.getId(), feature.getId(), DesignPlatform.WEB, CellState.FAIL);

            // when & then
            mockMvc.perform(put("/api/v1/design-review-items/" + item.getId())
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"state\": \"NOT_APPLICABLE\", \"version\": 0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("DESIGN_4001"));
        }
    }

    private Feature 기능(
            Sprint sprint,
            boolean changed
    ) {
        Feature feature = sprintFixture.기능("LRN-02", List.of(Part.SERVER, Part.WEB, Part.APP));
        sprintFixture.대상_기능(sprint.getId(), feature.getId(), changed);
        return feature;
    }

    private ResultActions 체크리스트(
            long sprintId,
            String round
    ) throws Exception {
        return mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/checklist").param("round", round).header("Authorization", BEARER));
    }

    private ResultActions 셀_변경(
            long itemId,
            String part,
            String state,
            int version
    ) throws Exception {
        return mockMvc.perform(put("/api/v1/checklist-items/" + itemId + "/cells/" + part)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\": \"" + state + "\", \"version\": " + version + "}"));
    }
}
