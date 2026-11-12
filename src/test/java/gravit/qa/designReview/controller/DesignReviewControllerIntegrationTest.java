package gravit.qa.designReview.controller;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.domain.DesignReviewItem;
import gravit.qa.designReview.repository.DesignReviewItemHistoryRepository;
import gravit.qa.designReview.repository.DesignReviewItemRepository;
import gravit.qa.feature.domain.Feature;
import gravit.qa.feature.domain.Part;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.service.JobCommandService;
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
import java.util.Map;

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
class DesignReviewControllerIntegrationTest {

    private static final String BEARER = "Bearer admin-access-token";
    private static final String ITEMS_PATH = "/api/v1/design-review-items/";
    private static final String EDIT_RESET_REASON = "항목 수정으로 판정 초기화";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SprintFixture sprintFixture;

    @Autowired
    private DesignReviewItemRepository designReviewItemRepository;

    @Autowired
    private DesignReviewItemHistoryRepository designReviewItemHistoryRepository;

    @Autowired
    private JobCommandService jobCommandService;

    @Nested
    @DisplayName("디자인 검수 항목 상태를 바꿀 때")
    class 상태_변경 {

        @Test
        void MANUAL_REVIEW에서_사람이_바꾸면_이력에_사람_변경이_남는다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when & then
            상태_변경(item.getId(), "PASS", 0)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("PASS"))
                    .andExpect(jsonPath("$.source").value("MANUAL"))
                    .andExpect(jsonPath("$.reason").value(nullValue()));
            이력(item.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].state").value("PASS"))
                    .andExpect(jsonPath("$[0].source").value("MANUAL"))
                    .andExpect(jsonPath("$[0].updatedBy").value(7));
        }

        @Test
        void 사람이_해당_없음으로_바꾸면_진행도와_검수_비율에서_빠지고_종료를_막지_않는다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem unchecked = 디자인_항목(sprint, CellState.UNCHECKED);
            디자인_항목(sprint, CellState.PASS);

            // when
            상태_변경(unchecked.getId(), "NOT_APPLICABLE", 0)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("NOT_APPLICABLE"))
                    .andExpect(jsonPath("$.source").value("MANUAL"))
                    .andExpect(jsonPath("$.editable").value(true));

            // then
            mockMvc.perform(get("/api/v1/sprints/" + sprint.getId()).header("Authorization", BEARER))
                    .andExpect(jsonPath("$.progress[0].part").value("DESIGN"))
                    .andExpect(jsonPath("$.progress[0].total").value(1))
                    .andExpect(jsonPath("$.progress[0].checked").value(1))
                    .andExpect(jsonPath("$.progress[0].pass").value(1));
            mockMvc.perform(get("/api/v1/sprints/" + sprint.getId() + "/design-review").header("Authorization", BEARER))
                    .andExpect(jsonPath("$.features[0].completionRate").value(100));
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
        }

        @Test
        void 해당_없음으로_바꾼_항목을_미검수로_되돌리면_종료를_막는다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);
            상태_변경(item.getId(), "NOT_APPLICABLE", 0).andExpect(status().isOk());

            // when
            상태_변경(item.getId(), "UNCHECKED", 1)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("UNCHECKED"));

            // then
            종료(sprint.getId(), sprint.getVersion())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4093"));
        }

        @Test
        void MANUAL_REVIEW가_아니면_409_SPRINT_4092() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.AUTO_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when & then
            상태_변경(item.getId(), "NOT_APPLICABLE", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
        }
    }

    @Nested
    @DisplayName("사람이 Fail 사유를 남길 때")
    class Fail_사유 {

        @Test
        void FAIL로_바꿀_때_보낸_사유는_앞뒤_공백을_지워_항목과_이력에_남긴다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.PASS);

            // when
            상태_변경(item.getId(), "FAIL", 0, "  애플 버튼이 맨 앞이다  ")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("FAIL"))
                    .andExpect(jsonPath("$.source").value("MANUAL"))
                    .andExpect(jsonPath("$.reason").value("애플 버튼이 맨 앞이다"));

            // then
            이력(item.getId())
                    .andExpect(jsonPath("$[0].state").value("FAIL"))
                    .andExpect(jsonPath("$[0].reason").value("애플 버튼이 맨 앞이다"));
        }

        @Test
        void FAIL이_아니면_사유를_무시하고_빈_사유는_null로_본다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem passed = 디자인_항목(sprint, CellState.FAIL);
            DesignReviewItem failed = 디자인_항목(sprint, CellState.PASS);

            // when & then
            상태_변경(passed.getId(), "PASS", 0, "무시되는 사유")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reason").value(nullValue()));
            상태_변경(failed.getId(), "FAIL", 0, "   ")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("FAIL"))
                    .andExpect(jsonPath("$.reason").value(nullValue()));
        }

        @Test
        void 이미_FAIL인_항목에_다른_사유를_보내면_사유만_바꾸고_이력을_남긴다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when
            상태_변경(item.getId(), "FAIL", 0, "사람 사유")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("FAIL"))
                    .andExpect(jsonPath("$.source").value("MANUAL"))
                    .andExpect(jsonPath("$.reason").value("사람 사유"))
                    .andExpect(jsonPath("$.version").value(1));

            // then
            이력(item.getId())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].state").value("FAIL"))
                    .andExpect(jsonPath("$[0].source").value("MANUAL"))
                    .andExpect(jsonPath("$[0].reason").value("사람 사유"));
        }

        @Test
        void 이미_FAIL인_항목에_사유_없이_FAIL을_보내면_바꾸지_않는다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when
            상태_변경(item.getId(), "FAIL", 0)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.source").value("AUTO"))
                    .andExpect(jsonPath("$.reason").value("자동 비교"))
                    .andExpect(jsonPath("$.version").value(0));

            // then
            이력(item.getId())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void 사유가_앞뒤_공백을_지운_뒤_500자를_넘으면_상태와_관계없이_400_GLOBAL_4001() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.PASS);

            // when & then
            상태_변경(item.getId(), "FAIL", 0, "가".repeat(501))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4001"));
            상태_변경(item.getId(), "UNCHECKED", 0, "가".repeat(501))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4001"));
            상태_변경(item.getId(), "FAIL", 0, "  " + "가".repeat(500) + "  ")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reason").value("가".repeat(500)));
        }
    }

    @Nested
    @DisplayName("디자인 검수 항목을 수정할 때")
    class 수정 {

        @Test
        void 행위를_바꾸면_판정을_UNCHECKED로_되돌리고_이력에_사유를_남긴다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when
            수정(item.getId(), "{\"title\": \"  다른 행위  \", \"version\": 0}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("다른 행위"))
                    .andExpect(jsonPath("$.expected").value("버튼 순서를 확인한다"))
                    .andExpect(jsonPath("$.state").value("UNCHECKED"))
                    .andExpect(jsonPath("$.source").value("MANUAL"))
                    .andExpect(jsonPath("$.reason").value(EDIT_RESET_REASON))
                    .andExpect(jsonPath("$.version").value(1));

            // then
            이력(item.getId())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].state").value("UNCHECKED"))
                    .andExpect(jsonPath("$[0].source").value("MANUAL"))
                    .andExpect(jsonPath("$[0].reason").value(EDIT_RESET_REASON))
                    .andExpect(jsonPath("$[0].updatedBy").value(7));
        }

        @Test
        void 해당_없음이던_항목도_설명을_바꾸면_UNCHECKED가_된다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.UNCHECKED);
            상태_변경(item.getId(), "NOT_APPLICABLE", 0).andExpect(status().isOk());

            // when & then
            수정(item.getId(), "{\"expected\": \"다른 설명\", \"version\": 1}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.expected").value("다른 설명"))
                    .andExpect(jsonPath("$.state").value("UNCHECKED"))
                    .andExpect(jsonPath("$.reason").value(EDIT_RESET_REASON));
        }

        @Test
        void 내용이_같으면_판정을_그대로_두고_이력을_남기지_않는다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when
            수정(item.getId(), "{\"title\": \" 로그인 버튼 영역 시안이 명세와 같다 \", \"version\": 0}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("FAIL"))
                    .andExpect(jsonPath("$.source").value("AUTO"))
                    .andExpect(jsonPath("$.version").value(0));

            // then
            이력(item.getId()).andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void 이미_사람이_미검수로_둔_항목도_내용을_바꾸면_초기화_기록을_1건_남긴다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);
            상태_변경(item.getId(), "UNCHECKED", 0).andExpect(status().isOk());

            // when
            수정(item.getId(), "{\"title\": \"다른 행위\", \"version\": 1}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("다른 행위"))
                    .andExpect(jsonPath("$.state").value("UNCHECKED"))
                    .andExpect(jsonPath("$.reason").value("항목 수정으로 판정 초기화"));

            // then
            이력(item.getId())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].state").value("UNCHECKED"))
                    .andExpect(jsonPath("$[0].source").value("MANUAL"))
                    .andExpect(jsonPath("$[0].reason").value("항목 수정으로 판정 초기화"));
        }

        @Test
        void MANUAL_REVIEW가_아니면_409_SPRINT_4092() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.AUTO_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when & then
            수정(item.getId(), "{\"title\": \"다른 행위\", \"version\": 0}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
        }

        @Test
        void 실행_중인_작업이_있으면_409_SPRINT_4092() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);
            jobCommandService.enqueue(JobType.QA_PREPARE, sprint.getId(), Map.of());

            // when & then
            수정(item.getId(), "{\"title\": \"다른 행위\", \"version\": 0}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
            assertThat(designReviewItemRepository.findById(item.getId()).orElseThrow().getTitle()).isEqualTo(item.getTitle());
        }

        @Test
        void 버전이_다르면_409_GLOBAL_4091() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when & then
            수정(item.getId(), "{\"title\": \"다른 행위\", \"version\": 3}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4091"));
        }

        @Test
        void 빈_행위나_version_누락은_400_GLOBAL_4001() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when & then
            수정(item.getId(), "{\"title\": \"\", \"version\": 0}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4001"));
            수정(item.getId(), "{\"title\": \"다른 행위\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4001"));
        }
    }

    @Nested
    @DisplayName("디자인 검수 항목을 지울 때")
    class 삭제 {

        @Test
        void 지우면_204이고_이력도_함께_지워진다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);
            상태_변경(item.getId(), "PASS", 0).andExpect(status().isOk());

            // when
            삭제(item.getId(), "1").andExpect(status().isNoContent());

            // then
            assertThat(designReviewItemRepository.findById(item.getId())).isEmpty();
            assertThat(designReviewItemHistoryRepository.findAllByItemIdOrderByChangedAtDescIdDesc(item.getId())).isEmpty();
            mockMvc.perform(get("/api/v1/sprints/" + sprint.getId() + "/design-review").header("Authorization", BEARER))
                    .andExpect(jsonPath("$.features", hasSize(0)));
        }

        @Test
        void version이_없으면_400_GLOBAL_4001() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when & then
            mockMvc.perform(delete(ITEMS_PATH + item.getId()).header("Authorization", BEARER))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4001"));
        }

        @Test
        void MANUAL_REVIEW가_아니면_지우지_않고_409_SPRINT_4092() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.DESIGN_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when & then
            삭제(item.getId(), "0")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
            assertThat(designReviewItemRepository.findById(item.getId())).isPresent();
        }

        @Test
        void 실행_중인_작업이_있으면_지우지_않고_409_SPRINT_4092() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);
            jobCommandService.enqueue(JobType.QA_PREPARE, sprint.getId(), Map.of());

            // when & then
            삭제(item.getId(), "0")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SPRINT_4092"));
            assertThat(designReviewItemRepository.findById(item.getId())).isPresent();
        }

        @Test
        void 버전이_다르면_지우지_않고_409_GLOBAL_4091() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SprintStatus.MANUAL_REVIEW);
            DesignReviewItem item = 디자인_항목(sprint, CellState.FAIL);

            // when & then
            삭제(item.getId(), "3")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4091"));
            assertThat(designReviewItemRepository.findById(item.getId())).isPresent();
        }

        @Test
        void 없는_항목은_404_DESIGN_4041() throws Exception {
            // when & then
            삭제(999L, "0")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("DESIGN_4041"));
        }
    }

    private DesignReviewItem 디자인_항목(
            Sprint sprint,
            CellState state
    ) {
        Feature feature = sprintFixture.기능("LRN-02-" + state, List.of(Part.WEB, Part.APP));
        sprintFixture.대상_기능(sprint.getId(), feature.getId(), false);
        return sprintFixture.디자인_항목(sprint.getId(), feature.getId(), DesignPlatform.WEB, state);
    }

    private ResultActions 상태_변경(
            long itemId,
            String state,
            int version
    ) throws Exception {
        return mockMvc.perform(put(ITEMS_PATH + itemId)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\": \"" + state + "\", \"version\": " + version + "}"));
    }

    private ResultActions 상태_변경(
            long itemId,
            String state,
            int version,
            String reason
    ) throws Exception {
        return mockMvc.perform(put(ITEMS_PATH + itemId)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\": \"" + state + "\", \"version\": " + version + ", \"reason\": \"" + reason + "\"}"));
    }

    private ResultActions 수정(
            long itemId,
            String body
    ) throws Exception {
        return mockMvc.perform(patch(ITEMS_PATH + itemId)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions 삭제(
            long itemId,
            String version
    ) throws Exception {
        return mockMvc.perform(delete(ITEMS_PATH + itemId).param("version", version).header("Authorization", BEARER));
    }

    private ResultActions 이력(long itemId) throws Exception {
        return mockMvc.perform(get(ITEMS_PATH + itemId + "/history").header("Authorization", BEARER));
    }

    private ResultActions 종료(
            long sprintId,
            int version
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/close")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": " + version + "}"));
    }
}
