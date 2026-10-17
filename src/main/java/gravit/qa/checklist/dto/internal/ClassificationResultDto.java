package gravit.qa.checklist.dto.internal;

import gravit.qa.checklist.domain.Classification;

public record ClassificationResultDto(
        Classification classification,
        boolean automationGap
) {

    public static ClassificationResultDto of(
            Classification classification,
            boolean automationGap
    ) {
        return new ClassificationResultDto(classification, automationGap);
    }
}
