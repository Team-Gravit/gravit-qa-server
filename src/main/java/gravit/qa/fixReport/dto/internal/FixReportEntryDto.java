package gravit.qa.fixReport.dto.internal;

import gravit.qa.checklist.dto.internal.CodeEvidenceDto;

import java.util.List;

public record FixReportEntryDto(
        String title,
        String expected,
        String reason,
        List<String> specRowIds,
        List<CodeEvidenceDto> evidence,
        List<String> figmaNodeIds
) {

    public static FixReportEntryDto of(
            String title,
            String expected,
            String reason,
            List<String> specRowIds,
            List<CodeEvidenceDto> evidence,
            List<String> figmaNodeIds
    ) {
        return new FixReportEntryDto(title, expected, reason, List.copyOf(specRowIds), List.copyOf(evidence), List.copyOf(figmaNodeIds));
    }
}
