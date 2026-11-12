package gravit.qa.designReview.dto.internal;

import gravit.qa.checklist.dto.internal.FailRecordDto;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.domain.DesignReviewItem;

import java.util.List;

public record FailedDesignItemDto(
        long itemId,
        long featureId,
        DesignPlatform platform,
        int sortOrder,
        String title,
        String expected,
        List<String> specRowIds,
        List<String> figmaNodeIds,
        String reason,
        List<FailRecordDto> failRecords
) {

    public static FailedDesignItemDto of(
            DesignReviewItem item,
            List<FailRecordDto> failRecords
    ) {
        return new FailedDesignItemDto(
                item.getId(),
                item.getFeatureId(),
                item.getPlatform(),
                item.getSortOrder(),
                item.getTitle(),
                item.getExpected(),
                List.copyOf(item.getSpecRowIds()),
                List.copyOf(item.getFigmaNodeIds()),
                item.getReason(),
                List.copyOf(failRecords)
        );
    }
}
