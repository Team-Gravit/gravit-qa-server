package gravit.qa.sprint.dto.internal;

import java.time.LocalDate;
import java.util.List;

public record SetupResultDto(
        SetupBaselineDto baseline,
        LocalDate periodStart,
        LocalDate periodEnd,
        String figmaFileKey,
        List<SetupFeatureDraftDto> features,
        List<SetupSnapshotDto> snapshots
) {

    public static SetupResultDto of(
            SetupBaselineDto baseline,
            LocalDate periodStart,
            LocalDate periodEnd,
            String figmaFileKey,
            List<SetupFeatureDraftDto> features,
            List<SetupSnapshotDto> snapshots
    ) {
        return new SetupResultDto(baseline, periodStart, periodEnd, figmaFileKey, List.copyOf(features), List.copyOf(snapshots));
    }
}
