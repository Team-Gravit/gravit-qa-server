package gravit.qa.designReview.dto.response;

import gravit.qa.sprint.domain.SprintStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DesignReviewResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long sprintId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SprintStatus sprintStatus,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<DesignReviewFeatureResponse> features
) {

    public static DesignReviewResponse of(
            long sprintId,
            SprintStatus sprintStatus,
            List<DesignReviewFeatureResponse> features
    ) {
        return new DesignReviewResponse(sprintId, sprintStatus, List.copyOf(features));
    }
}
