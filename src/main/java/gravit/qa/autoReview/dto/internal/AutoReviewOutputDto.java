package gravit.qa.autoReview.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record AutoReviewOutputDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("입력의 체크 항목마다 판정 1개")
        List<AutoReviewResultOutputDto> results
) {
}
