package gravit.qa.checklist.dto.internal;

import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;

import java.util.List;

public record FailedCellDto(
        long itemId,
        long featureId,
        Classification classification,
        int sortOrder,
        String title,
        String expected,
        List<String> specRowIds,
        String reason,
        List<CodeEvidenceDto> evidence,
        List<FailRecordDto> failRecords
) {

    public static FailedCellDto of(
            ChecklistItem item,
            CheckCell cell,
            List<FailRecordDto> failRecords
    ) {
        return new FailedCellDto(
                item.getId(),
                item.getFeatureId(),
                item.getClassification(),
                item.getSortOrder(),
                item.getTitle(),
                item.getExpected(),
                List.copyOf(item.getSpecRowIds()),
                cell.getReason(),
                List.copyOf(cell.getEvidence()),
                List.copyOf(failRecords)
        );
    }
}
