package gravit.qa.feature.dto.internal;

import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;

import java.util.List;

public record FeatureUpsertDto(
        String screenId,
        String name,
        String route,
        List<Part> parts,
        String notionPageId,
        SpecStatus specStatus,
        List<String> relatedOperationIds
) {

    public static FeatureUpsertDto of(
            String screenId,
            String name,
            String route,
            List<Part> parts,
            String notionPageId,
            SpecStatus specStatus,
            List<String> relatedOperationIds
    ) {
        return new FeatureUpsertDto(screenId, name, route, parts, notionPageId, specStatus, relatedOperationIds);
    }
}
