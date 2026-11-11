package gravit.qa.sprint.dto.internal;

import gravit.qa.adapter.notion.dto.internal.NotionSpecDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecTableDto;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;

import java.util.List;

public record SetupFeatureSpecDto(
        NotionSpecDto spec,
        NotionSpecTableDto table,
        List<Part> parts,
        SpecStatus specStatus,
        String bodyMarkdown
) {

    public static SetupFeatureSpecDto of(
            NotionSpecDto spec,
            NotionSpecTableDto table,
            List<Part> parts,
            SpecStatus specStatus,
            String bodyMarkdown
    ) {
        return new SetupFeatureSpecDto(spec, table, List.copyOf(parts), specStatus, bodyMarkdown);
    }

    public String screenId() {
        return spec.screenId();
    }
}
