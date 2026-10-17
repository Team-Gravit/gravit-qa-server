package gravit.qa.sprint.dto.internal;

import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;

import java.util.List;
import java.util.Map;

public record SetupNotionDto(
        NotionSprintDto sprint,
        String figmaFileKey,
        List<SetupFeatureSpecDto> specs,
        List<SetupSnapshotDto> snapshots,
        Map<String, List<WarningDto>> screenIdToWarnings
) {

    public static SetupNotionDto of(
            NotionSprintDto sprint,
            String figmaFileKey,
            List<SetupFeatureSpecDto> specs,
            List<SetupSnapshotDto> snapshots,
            Map<String, List<WarningDto>> screenIdToWarnings
    ) {
        return new SetupNotionDto(sprint, figmaFileKey, List.copyOf(specs), List.copyOf(snapshots), Map.copyOf(screenIdToWarnings));
    }

    public List<WarningDto> warningsOf(String screenId) {
        return screenIdToWarnings.getOrDefault(screenId, List.of());
    }
}
