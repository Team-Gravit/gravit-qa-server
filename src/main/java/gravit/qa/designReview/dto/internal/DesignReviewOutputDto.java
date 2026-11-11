package gravit.qa.designReview.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record DesignReviewOutputDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("디자인 검수 항목. 플랫폼마다 그 플랫폼의 C행을 정확히 한 번씩 다룬다")
        List<DesignReviewItemOutputDto> items
) {
}
