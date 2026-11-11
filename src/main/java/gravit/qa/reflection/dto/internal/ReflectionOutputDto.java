package gravit.qa.reflection.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"evidence", "operations", "skippedRows"})
public record ReflectionOutputDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("수정 이력 행마다 본문의 어느 부분을 어떻게 바꾸는지 먼저 쓴다")
        String evidence,

        @JsonProperty(required = true)
        @JsonPropertyDescription("본문 편집 목록. 적힌 순서대로 적용한다")
        List<ReflectionOperationOutputDto> operations,

        @JsonProperty(required = true)
        @JsonPropertyDescription("편집 없이 넘기는 수정 이력 행(이미 본문에 반영된 경우 등)과 이유. 없으면 빈 배열")
        List<ReflectionSkippedRowOutputDto> skippedRows
) {
}
