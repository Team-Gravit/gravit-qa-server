package gravit.qa.sprint.dto.internal;

import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;

import java.util.List;
import java.util.Map;

public record SetupNotionDto(
        NotionSprintDto sprint,
        String figmaFileKey,
        List<SetupFeatureSpecDto> specs,
        List<SetupSnapshotDto> snapshots,
        Map<String, List<WarningDto>> screenIdToWarnings,
        List<WarningDto> sprintWarnings
) {

    public static SetupNotionDto of(
            NotionSprintDto sprint,
            String figmaFileKey,
            List<SetupFeatureSpecDto> specs,
            List<SetupSnapshotDto> snapshots,
            Map<String, List<WarningDto>> screenIdToWarnings,
            List<WarningDto> sprintWarnings
    ) {
        return new SetupNotionDto(sprint, figmaFileKey, List.copyOf(specs), List.copyOf(snapshots), Map.copyOf(screenIdToWarnings), List.copyOf(sprintWarnings));
    }

    public List<WarningDto> warningsOf(String screenId) {
        return screenIdToWarnings.getOrDefault(screenId, List.of());
    }
}
