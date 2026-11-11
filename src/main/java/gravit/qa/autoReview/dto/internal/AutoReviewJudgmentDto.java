package gravit.qa.autoReview.dto.internal;

import gravit.qa.checklist.domain.Verdict;

public record AutoReviewJudgmentDto(
        long itemId,
        Verdict verdict,
        String reason
) {

    public static AutoReviewJudgmentDto of(
            long itemId,
            Verdict verdict,
            String reason
    ) {
        return new AutoReviewJudgmentDto(itemId, verdict, reason);
    }
}
