package gravit.qa.adapter.notion.dto.internal;

import java.util.List;

public record NotionSpecTableDto(
        String specStatus,
        List<String> parts,
        String route,
        List<String> relatedApis,
        String figmaUrl,
        List<String> feSpecIds
) {

    public static NotionSpecTableDto of(
            String specStatus,
            List<String> parts,
            String route,
            List<String> relatedApis,
            String figmaUrl,
            List<String> feSpecIds
    ) {
        return new NotionSpecTableDto(specStatus, List.copyOf(parts), route, List.copyOf(relatedApis), figmaUrl, List.copyOf(feSpecIds));
    }
}
