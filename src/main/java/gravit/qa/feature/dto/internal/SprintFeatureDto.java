package gravit.qa.feature.dto.internal;

import gravit.qa.sprint.dto.internal.WarningDto;

import java.util.List;

public record SprintFeatureDto(
        FeatureDto feature,
        List<String> figmaNodeIds,
        List<WarningDto> warnings
) {

    public static SprintFeatureDto of(
            FeatureDto feature,
            List<String> figmaNodeIds,
            List<WarningDto> warnings
    ) {
        return new SprintFeatureDto(feature, List.copyOf(figmaNodeIds), List.copyOf(warnings));
    }
}
