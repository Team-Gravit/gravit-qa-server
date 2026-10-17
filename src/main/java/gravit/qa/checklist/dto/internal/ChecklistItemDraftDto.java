package gravit.qa.checklist.dto.internal;

import gravit.qa.checklist.domain.Classification;
import gravit.qa.feature.domain.Part;

import java.util.List;

public record ChecklistItemDraftDto(
        String title,
        String expected,
        List<Part> parts,
        Classification classification,
        ClassificationCriteriaDto criteria,
        boolean automationGap,
        String testRef,
        String personaKey
) {

    public static ChecklistItemDraftDto of(
            String title,
            String expected,
            List<Part> parts,
            Classification classification,
            ClassificationCriteriaDto criteria,
            boolean automationGap,
            String testRef,
            String personaKey
    ) {
        return new ChecklistItemDraftDto(title, expected, parts, classification, criteria, automationGap, testRef, personaKey);
    }
}
