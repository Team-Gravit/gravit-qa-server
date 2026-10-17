package gravit.qa.checklist.dto.internal;

import java.util.List;

public record ChecklistProposalDto(
        long featureId,
        List<ChecklistItemDraftDto> drafts,
        List<Long> sourceRefs
) {

    public static ChecklistProposalDto of(
            long featureId,
            List<ChecklistItemDraftDto> drafts,
            List<Long> sourceRefs
    ) {
        return new ChecklistProposalDto(featureId, List.copyOf(drafts), List.copyOf(sourceRefs));
    }
}
