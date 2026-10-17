package gravit.qa.checklist.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.stream.Stream;

@JsonPropertyOrder({"specDecidable", "preconditionReproducible", "observable", "noExternalDependency", "notSubjective"})
public record ClassificationCriteriaDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("명세만으로 통과와 실패를 판정할 수 있는가")
        CriterionResultDto specDecidable,

        @JsonProperty(required = true)
        @JsonPropertyDescription("사전 조건을 페르소나 데이터셋으로 매번 같게 재현할 수 있는가")
        CriterionResultDto preconditionReproducible,

        @JsonProperty(required = true)
        @JsonPropertyDescription("결과를 화면 요소나 API 응답으로 관찰할 수 있는가")
        CriterionResultDto observable,

        @JsonProperty(required = true)
        @JsonPropertyDescription("소셜 로그인, 결제, 푸시, 실제 시간 경과 같은 외부 의존이 없는가")
        CriterionResultDto noExternalDependency,

        @JsonProperty(required = true)
        @JsonPropertyDescription("디자인 감성, 사용성 인상 같은 주관 판단이 필요 없는가")
        CriterionResultDto notSubjective
) {

    public boolean allMet() {
        return Stream.of(specDecidable, preconditionReproducible, observable, noExternalDependency, notSubjective)
                .allMatch(criterion -> criterion != null && criterion.met());
    }
}
