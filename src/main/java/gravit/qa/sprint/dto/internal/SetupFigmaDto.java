package gravit.qa.sprint.dto.internal;

import gravit.qa.adapter.figma.dto.internal.FigmaScreenDto;
import gravit.qa.feature.domain.Part;

import java.util.List;
import java.util.Map;

public record SetupFigmaDto(
        Map<String, List<FigmaScreenDto>> screenIdToScreens,
        Map<String, List<String>> screenIdToNodeIds,
        Map<String, List<Part>> screenIdToMissingParts,
        Map<String, List<WarningDto>> screenIdToWarnings,
        List<SetupSnapshotDto> snapshots
) {

    public static SetupFigmaDto of(
            Map<String, List<FigmaScreenDto>> screenIdToScreens,
            Map<String, List<String>> screenIdToNodeIds,
            Map<String, List<Part>> screenIdToMissingParts,
            Map<String, List<WarningDto>> screenIdToWarnings,
            List<SetupSnapshotDto> snapshots
    ) {
        return new SetupFigmaDto(
                Map.copyOf(screenIdToScreens),
                Map.copyOf(screenIdToNodeIds),
                Map.copyOf(screenIdToMissingParts),
                Map.copyOf(screenIdToWarnings),
                List.copyOf(snapshots)
        );
    }

    public List<FigmaScreenDto> screensOf(String screenId) {
        return screenIdToScreens.getOrDefault(screenId, List.of());
    }

    public List<String> nodeIdsOf(String screenId) {
        return screenIdToNodeIds.getOrDefault(screenId, List.of());
    }

    public List<Part> missingPartsOf(String screenId) {
        return screenIdToMissingParts.getOrDefault(screenId, List.of());
    }

    public List<WarningDto> warningsOf(String screenId) {
        return screenIdToWarnings.getOrDefault(screenId, List.of());
    }
}
