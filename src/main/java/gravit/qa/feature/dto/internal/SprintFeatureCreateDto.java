package gravit.qa.feature.dto.internal;

import gravit.qa.sprint.dto.internal.WarningDto;

import java.util.List;

public record SprintFeatureCreateDto(
        long featureId,
        List<String> figmaNodeIds,
        List<WarningDto> warnings
) {

    public static SprintFeatureCreateDto of(
            long featureId,
            List<String> figmaNodeIds,
            List<WarningDto> warnings
    ) {
        return new SprintFeatureCreateDto(featureId, figmaNodeIds, warnings);
    }
}
