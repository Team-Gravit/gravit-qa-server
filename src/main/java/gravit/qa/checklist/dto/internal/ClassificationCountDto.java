package gravit.qa.checklist.dto.internal;

import gravit.qa.checklist.domain.Classification;

public record ClassificationCountDto(
        Classification classification,
        long count
) {
}
