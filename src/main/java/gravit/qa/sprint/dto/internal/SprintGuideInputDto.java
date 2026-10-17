package gravit.qa.sprint.dto.internal;

import gravit.qa.feature.domain.Part;
import gravit.qa.sprint.domain.PrepareFailureReason;
import gravit.qa.sprint.domain.SprintStatus;

import java.util.Map;

public record SprintGuideInputDto(
        SprintStatus status,
        PrepareFailureReason prepareFailureReason,
        long proposedCount,
        Map<Part, Long> partToUncheckedCount,
        boolean autoRunActive
) {

    public static SprintGuideInputDto of(
            SprintStatus status,
            PrepareFailureReason prepareFailureReason,
            long proposedCount,
            Map<Part, Long> partToUncheckedCount,
            boolean autoRunActive
    ) {
        return new SprintGuideInputDto(status, prepareFailureReason, proposedCount, Map.copyOf(partToUncheckedCount), autoRunActive);
    }
}
