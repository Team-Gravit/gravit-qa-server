package gravit.qa.sprint.dto.internal;

import gravit.qa.adapter.notion.dto.internal.NotionSpecDto;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;

import java.util.List;

public record SetupFeatureSpecDto(
        NotionSpecDto spec,
        List<Part> parts,
        SpecStatus specStatus,
        String bodyMarkdown
) {

    public static SetupFeatureSpecDto of(
            NotionSpecDto spec,
            List<Part> parts,
            SpecStatus specStatus,
            String bodyMarkdown
    ) {
        return new SetupFeatureSpecDto(spec, List.copyOf(parts), specStatus, bodyMarkdown);
    }

    public String screenId() {
        return spec.screenId();
    }
}
