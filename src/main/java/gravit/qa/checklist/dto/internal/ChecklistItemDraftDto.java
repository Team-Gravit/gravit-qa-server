package gravit.qa.checklist.dto.internal;

import gravit.qa.checklist.domain.Classification;
import gravit.qa.feature.domain.Part;

import java.util.List;

public record ChecklistItemDraftDto(
        String title,
        String expected,
        List<Part> parts,
        Classification classification,
        List<String> specRowIds
) {

    public static ChecklistItemDraftDto of(
            String title,
            String expected,
            List<Part> parts,
            Classification classification,
            List<String> specRowIds
    ) {
        return new ChecklistItemDraftDto(title, expected, List.copyOf(parts), classification, List.copyOf(specRowIds));
    }
}
