package gravit.qa.autoReview.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"path", "startLine", "endLine"})
public record CodeEvidenceOutputDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("입력 코드의 파일 경로(### 뒤의 경로 그대로)")
        String path,

        @JsonProperty(required = true)
        @JsonPropertyDescription("근거 시작 줄 번호")
        int startLine,

        @JsonProperty(required = true)
        @JsonPropertyDescription("근거 끝 줄 번호")
        int endLine
) {
}
