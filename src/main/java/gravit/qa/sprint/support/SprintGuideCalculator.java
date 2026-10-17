package gravit.qa.sprint.support;

import gravit.qa.feature.domain.Part;
import gravit.qa.sprint.domain.GuideAction;
import gravit.qa.sprint.domain.GuideBlockerCode;
import gravit.qa.sprint.domain.GuideStage;
import gravit.qa.sprint.domain.PrepareFailureReason;
import gravit.qa.sprint.dto.internal.SprintGuideInputDto;
import gravit.qa.sprint.dto.response.GuideBlockerResponse;
import gravit.qa.sprint.dto.response.SprintGuideResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SprintGuideCalculator {

    private static final int STAGE_COUNT = GuideStage.values().length;

    public static SprintGuideResponse calculate(SprintGuideInputDto input) {
        return switch (input.status()) {
            case DRAFT -> guide(GuideStage.PREPARE, GuideAction.START_SETUP, List.of());
            case PREPARING -> guide(GuideStage.PREPARE, GuideAction.WAIT_SETUP, List.of(blocker(GuideBlockerCode.SETUP_IN_PROGRESS)));
            case PREPARE_FAILED -> guide(GuideStage.PREPARE, GuideAction.RETRY_SETUP, prepareFailureBlockers(input.prepareFailureReason()));
            case REVIEW -> review(input.proposedCount());
            case IN_PROGRESS -> inProgress(input);
            case CLOSED -> guide(GuideStage.DONE, GuideAction.NONE, List.of());
        };
    }

    private static List<GuideBlockerResponse> prepareFailureBlockers(PrepareFailureReason reason) {
        if (reason == PrepareFailureReason.RELEASE_BRANCH_MISSING) {
            return List.of(blocker(GuideBlockerCode.RELEASE_BRANCH_MISSING));
        }
        return List.of();
    }

    private static SprintGuideResponse review(long proposedCount) {
        if (proposedCount > 0) {
            return guide(GuideStage.REVIEW, GuideAction.REVIEW_ITEMS, List.of(GuideBlockerResponse.of(GuideBlockerCode.PROPOSED_ITEMS_REMAINING, proposedCount, null)));
        }
        return guide(GuideStage.REVIEW, GuideAction.CONFIRM_CHECKLIST, List.of());
    }

    private static SprintGuideResponse inProgress(SprintGuideInputDto input) {
        List<GuideBlockerResponse> blockers = new ArrayList<>();
        if (input.autoRunActive()) {
            blockers.add(blocker(GuideBlockerCode.AUTO_RUN_IN_PROGRESS));
        }
        for (Part part : Part.values()) {
            long unchecked = input.partToUncheckedCount().getOrDefault(part, 0L);
            if (unchecked > 0) {
                blockers.add(GuideBlockerResponse.of(GuideBlockerCode.UNCHECKED_CELLS_REMAINING, unchecked, part));
            }
        }

        if (input.autoRunActive()) {
            return guide(GuideStage.CHECK, GuideAction.WAIT_AUTO_RUN, blockers);
        }
        if (!blockers.isEmpty()) {
            return guide(GuideStage.CHECK, GuideAction.CHECK_CELLS, blockers);
        }
        return guide(GuideStage.CLOSE, GuideAction.CLOSE_SPRINT, List.of());
    }

    private static GuideBlockerResponse blocker(GuideBlockerCode code) {
        return GuideBlockerResponse.of(code, null, null);
    }

    private static SprintGuideResponse guide(
            GuideStage stage,
            GuideAction action,
            List<GuideBlockerResponse> blockers
    ) {
        return SprintGuideResponse.of(stage, STAGE_COUNT, action, blockers);
    }
}
