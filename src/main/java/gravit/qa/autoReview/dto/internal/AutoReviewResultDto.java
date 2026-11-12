package gravit.qa.autoReview.dto.internal;

import java.util.List;

public record AutoReviewResultDto(
        List<AutoReviewJudgmentDto> judgments,
        int calls,
        int files,
        int promptChars,
        List<String> droppedFiles,
        boolean failed
) {

    public static AutoReviewResultDto of(
            List<AutoReviewJudgmentDto> judgments,
            int calls,
            int files,
            int promptChars,
            List<String> droppedFiles,
            boolean failed
    ) {
        return new AutoReviewResultDto(List.copyOf(judgments), calls, files, promptChars, List.copyOf(droppedFiles), failed);
    }
}
