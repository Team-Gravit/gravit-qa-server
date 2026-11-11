package gravit.qa.designReview.dto.internal;

import java.util.List;

public record DesignReviewResultDto(
        List<DesignReviewItemDraftDto> items,
        int calls,
        int promptChars,
        boolean truncated,
        boolean failed
) {

    public static DesignReviewResultDto of(
            List<DesignReviewItemDraftDto> items,
            int calls,
            int promptChars,
            boolean truncated,
            boolean failed
    ) {
        return new DesignReviewResultDto(List.copyOf(items), calls, promptChars, truncated, failed);
    }
}
