package gravit.qa.autoReview.dto.internal;

import gravit.qa.checklist.domain.Verdict;
import gravit.qa.checklist.dto.internal.CodeEvidenceDto;

import java.util.List;

public record AutoReviewJudgmentDto(
        long itemId,
        Verdict verdict,
        String reason,
        List<CodeEvidenceDto> evidence
) {

    public static AutoReviewJudgmentDto of(
            long itemId,
            Verdict verdict,
            String reason,
            List<CodeEvidenceDto> evidence
    ) {
        return new AutoReviewJudgmentDto(itemId, verdict, reason, List.copyOf(evidence));
    }

    public static AutoReviewJudgmentDto unknown(
            long itemId,
            String reason
    ) {
        return of(itemId, Verdict.UNKNOWN, reason, List.of());
    }
}
