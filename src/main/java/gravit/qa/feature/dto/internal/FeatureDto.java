package gravit.qa.feature.dto.internal;

import gravit.qa.feature.domain.Feature;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;

import java.util.List;

public record FeatureDto(
        long id,
        String screenId,
        String name,
        String route,
        List<Part> parts,
        SpecStatus specStatus,
        String notionPageId
) {

    public static FeatureDto from(Feature feature) {
        return new FeatureDto(
                feature.getId(),
                feature.getScreenId(),
                feature.getName(),
                feature.getRoute(),
                List.copyOf(feature.getParts()),
                feature.getSpecStatus(),
                feature.getNotionPageId()
        );
    }
}
