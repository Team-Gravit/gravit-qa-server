package gravit.qa.reflection.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"historyRow", "reason"})
public record ReflectionSkippedRowOutputDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("편집 없이 넘기는 수정 이력 행 번호(1부터)")
        int historyRow,

        @JsonProperty(required = true)
        @JsonPropertyDescription("넘기는 이유. 예: 본문 C3 행에 이미 같은 내용이 있음")
        String reason
) {
}
