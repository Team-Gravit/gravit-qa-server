package gravit.qa.checklist.dto.internal;

import java.util.List;

public record FailRecordDto(
        String reason,
        List<CodeEvidenceDto> evidence
) {

    public static FailRecordDto of(
            String reason,
            List<CodeEvidenceDto> evidence
    ) {
        return new FailRecordDto(reason, List.copyOf(evidence));
    }
}
