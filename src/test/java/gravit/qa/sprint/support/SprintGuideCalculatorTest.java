package gravit.qa.sprint.support;

import gravit.qa.feature.domain.Part;
import gravit.qa.sprint.domain.GuideAction;
import gravit.qa.sprint.domain.GuideBlockerCode;
import gravit.qa.sprint.domain.GuideStage;
import gravit.qa.sprint.domain.PrepareFailureReason;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.SprintGuideInputDto;
import gravit.qa.sprint.dto.response.GuideBlockerResponse;
import gravit.qa.sprint.dto.response.SprintGuideResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SprintGuideCalculatorTest {

    private static final Map<Part, Long> NO_UNCHECKED = Map.of();
    private static final Map<Part, Long> WEB_AND_APP_UNCHECKED = Map.of(Part.SERVER, 0L, Part.WEB, 2L, Part.APP, 1L);

    static Stream<Arguments> 안내_표() {
        return Stream.of(
                arguments("DRAFT", 입력(SprintStatus.DRAFT, null, 0, NO_UNCHECKED, false),
                        GuideStage.PREPARE, GuideAction.START_SETUP, List.of()),
                arguments("PREPARING", 입력(SprintStatus.PREPARING, null, 0, NO_UNCHECKED, false),
                        GuideStage.PREPARE, GuideAction.WAIT_SETUP, List.of(막힘(GuideBlockerCode.SETUP_IN_PROGRESS, null, null))),
                arguments("PREPARE_FAILED 브랜치 없음", 입력(SprintStatus.PREPARE_FAILED, PrepareFailureReason.RELEASE_BRANCH_MISSING, 0, NO_UNCHECKED, false),
                        GuideStage.PREPARE, GuideAction.RETRY_SETUP, List.of(막힘(GuideBlockerCode.RELEASE_BRANCH_MISSING, null, null))),
                arguments("PREPARE_FAILED 그 외", 입력(SprintStatus.PREPARE_FAILED, null, 0, NO_UNCHECKED, false),
                        GuideStage.PREPARE, GuideAction.RETRY_SETUP, List.of()),
                arguments("REVIEW 남은 제안 있음", 입력(SprintStatus.REVIEW, null, 3, NO_UNCHECKED, false),
                        GuideStage.REVIEW, GuideAction.REVIEW_ITEMS, List.of(막힘(GuideBlockerCode.PROPOSED_ITEMS_REMAINING, 3L, null))),
                arguments("REVIEW 남은 제안 없음", 입력(SprintStatus.REVIEW, null, 0, NO_UNCHECKED, false),
                        GuideStage.REVIEW, GuideAction.CONFIRM_CHECKLIST, List.of()),
                arguments("IN_PROGRESS 자동 실행 중, 미검수 있음", 입력(SprintStatus.IN_PROGRESS, null, 0, WEB_AND_APP_UNCHECKED, true),
                        GuideStage.CHECK, GuideAction.WAIT_AUTO_RUN, List.of(
                                막힘(GuideBlockerCode.AUTO_RUN_IN_PROGRESS, null, null),
                                막힘(GuideBlockerCode.UNCHECKED_CELLS_REMAINING, 2L, Part.WEB),
                                막힘(GuideBlockerCode.UNCHECKED_CELLS_REMAINING, 1L, Part.APP))),
                arguments("IN_PROGRESS 자동 실행 중, 미검수 없음", 입력(SprintStatus.IN_PROGRESS, null, 0, NO_UNCHECKED, true),
                        GuideStage.CHECK, GuideAction.WAIT_AUTO_RUN, List.of(막힘(GuideBlockerCode.AUTO_RUN_IN_PROGRESS, null, null))),
                arguments("IN_PROGRESS 미검수 있음", 입력(SprintStatus.IN_PROGRESS, null, 0, WEB_AND_APP_UNCHECKED, false),
                        GuideStage.CHECK, GuideAction.CHECK_CELLS, List.of(
                                막힘(GuideBlockerCode.UNCHECKED_CELLS_REMAINING, 2L, Part.WEB),
                                막힘(GuideBlockerCode.UNCHECKED_CELLS_REMAINING, 1L, Part.APP))),
                arguments("IN_PROGRESS 모두 검수", 입력(SprintStatus.IN_PROGRESS, null, 0, Map.of(Part.WEB, 0L), false),
                        GuideStage.CLOSE, GuideAction.CLOSE_SPRINT, List.of()),
                arguments("CLOSED", 입력(SprintStatus.CLOSED, null, 0, NO_UNCHECKED, false),
                        GuideStage.DONE, GuideAction.NONE, List.of())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("안내_표")
    void 스프린트_상태와_남은_일로_단계_다음_할_일_막힌_이유를_정한다(
            String caseName,
            SprintGuideInputDto input,
            GuideStage stage,
            GuideAction nextAction,
            List<GuideBlockerResponse> blockers
    ) {
        // when
        SprintGuideResponse guide = SprintGuideCalculator.calculate(input);

        // then
        assertSoftly(softly -> {
            softly.assertThat(guide.stage()).isEqualTo(stage);
            softly.assertThat(guide.stageIndex()).isEqualTo(stage.getIndex());
            softly.assertThat(guide.stageCount()).isEqualTo(5);
            softly.assertThat(guide.nextAction()).isEqualTo(nextAction);
            softly.assertThat(guide.blockers()).containsExactlyElementsOf(blockers);
        });
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("단계_순서")
    void 단계_위치는_PREPARE_1부터_DONE_5까지다(
            GuideStage stage,
            int index
    ) {
        // when & then
        assertSoftly(softly -> softly.assertThat(stage.getIndex()).isEqualTo(index));
    }

    static Stream<Arguments> 단계_순서() {
        return Stream.of(
                arguments(GuideStage.PREPARE, 1),
                arguments(GuideStage.REVIEW, 2),
                arguments(GuideStage.CHECK, 3),
                arguments(GuideStage.CLOSE, 4),
                arguments(GuideStage.DONE, 5)
        );
    }

    private static SprintGuideInputDto 입력(
            SprintStatus status,
            PrepareFailureReason reason,
            long proposedCount,
            Map<Part, Long> partToUncheckedCount,
            boolean autoRunActive
    ) {
        return SprintGuideInputDto.of(status, reason, proposedCount, partToUncheckedCount, autoRunActive);
    }

    private static GuideBlockerResponse 막힘(
            GuideBlockerCode code,
            Long count,
            Part part
    ) {
        return GuideBlockerResponse.of(code, count, part);
    }
}
