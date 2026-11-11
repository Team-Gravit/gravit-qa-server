package gravit.qa.feature.dto.internal;

import gravit.qa.feature.domain.SprintFeature;

import java.util.List;

public record SprintFeatureDto(
        FeatureDto feature,
        List<String> figmaNodeIds,
        boolean changed,
        String specMarkdown,
        List<SpecRowDto> specRows,
        List<SpecHistoryRowDto> historyRows
) {

    public static SprintFeatureDto of(
            FeatureDto feature,
            SprintFeature sprintFeature
    ) {
        return new SprintFeatureDto(
                feature,
                List.copyOf(sprintFeature.getFigmaNodeIds()),
                sprintFeature.isChanged(),
                sprintFeature.getSpecMarkdown(),
                List.copyOf(sprintFeature.getSpecRows()),
                List.copyOf(sprintFeature.getHistoryRows())
        );
    }

    public long featureId() {
        return feature.id();
    }

    public String screenId() {
        return feature.screenId();
    }
}
