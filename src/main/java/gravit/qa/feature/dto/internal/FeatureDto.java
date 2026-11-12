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
        List<String> relatedApis,
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
                List.copyOf(feature.getRelatedApis()),
                feature.getNotionPageId()
        );
    }
}
