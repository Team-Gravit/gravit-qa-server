package gravit.qa.checklist.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.feature.domain.Part;

import java.util.List;

@JsonPropertyOrder({"evidence", "specRowIds", "title", "expected", "parts", "classificationReason", "classification"})
public record ChecklistDraftItemOutputDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("이 항목의 근거. 명세 행 번호와 내용, OpenAPI 경로를 먼저 쓴다")
        String evidence,

        @JsonProperty(required = true)
        @JsonPropertyDescription("이 항목이 다루는 명세 행 번호. 입력에 있는 번호(C1, B2, E1 등)만 쓴다")
        List<String> specRowIds,

        @JsonProperty(required = true)
        @JsonPropertyDescription("행위. 짧은 문장으로 쓴다. 예: 로그인 제공자 버튼 순서를 표시한다")
        String title,

        @JsonProperty(required = true)
        @JsonPropertyDescription("설명. 세부 확인 내용을 근거에 있는 문구와 값으로 쓴다")
        String expected,

        @JsonProperty(required = true)
        @JsonPropertyDescription("적용 파트. 기능의 파트 안에서 1개 이상 고른다")
        List<Part> parts,

        @JsonProperty(required = true)
        @JsonPropertyDescription("분류 근거 한 문장. 코드를 읽어 판정할 수 있는지, 실제로 동작시켜야 하는지")
        String classificationReason,

        @JsonProperty(required = true)
        @JsonPropertyDescription("AUTO는 코드를 읽어 판정할 수 있는 것, MANUAL은 실제로 동작시켜야 확인할 수 있는 것")
        Classification classification
) {
}
