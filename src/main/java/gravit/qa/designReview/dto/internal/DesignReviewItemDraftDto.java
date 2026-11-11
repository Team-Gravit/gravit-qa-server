package gravit.qa.designReview.dto.internal;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.designReview.domain.DesignPlatform;

import java.util.List;

public record DesignReviewItemDraftDto(
        DesignPlatform platform,
        String title,
        String expected,
        List<String> specRowIds,
        List<String> figmaNodeIds,
        CellState state,
        String reason
) {

    public static DesignReviewItemDraftDto of(
            DesignPlatform platform,
            String title,
            String expected,
            List<String> specRowIds,
            List<String> figmaNodeIds,
            CellState state,
            String reason
    ) {
        return new DesignReviewItemDraftDto(platform, title, expected, List.copyOf(specRowIds), List.copyOf(figmaNodeIds), state, reason);
    }
}
