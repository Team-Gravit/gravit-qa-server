package gravit.qa.checklist.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"reason", "met"})
public record CriterionResultDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("판정 근거. 입력 자료의 어느 부분 때문에 충족 또는 미충족인지 한 문장으로 쓴다")
        String reason,

        @JsonProperty(required = true)
        @JsonPropertyDescription("기준 충족 여부")
        boolean met
) {
}
