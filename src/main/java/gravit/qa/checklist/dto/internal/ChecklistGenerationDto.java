package gravit.qa.checklist.dto.internal;

import java.util.List;

public record ChecklistGenerationDto(
        List<ChecklistItemDraftDto> items,
        int promptChars,
        boolean truncated,
        boolean failed
) {

    public static ChecklistGenerationDto of(
            List<ChecklistItemDraftDto> items,
            int promptChars,
            boolean truncated,
            boolean failed
    ) {
        return new ChecklistGenerationDto(List.copyOf(items), promptChars, truncated, failed);
    }
}
