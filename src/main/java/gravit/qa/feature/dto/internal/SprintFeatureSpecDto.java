package gravit.qa.feature.dto.internal;

import java.util.List;

public record SprintFeatureSpecDto(
        long featureId,
        boolean changed,
        String specMarkdown,
        List<SpecRowDto> specRows,
        List<SpecHistoryRowDto> historyRows
) {

    public static SprintFeatureSpecDto of(
            long featureId,
            boolean changed,
            String specMarkdown,
            List<SpecRowDto> specRows,
            List<SpecHistoryRowDto> historyRows
    ) {
        return new SprintFeatureSpecDto(featureId, changed, specMarkdown, List.copyOf(specRows), List.copyOf(historyRows));
    }
}
