package gravit.qa.designReview.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import gravit.qa.checklist.domain.Verdict;
import gravit.qa.designReview.domain.DesignPlatform;

import java.util.List;

@JsonPropertyOrder({"platform", "evidence", "specRowIds", "title", "expected", "figmaNodeIds", "reason", "verdict"})
public record DesignReviewItemOutputDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("WEB 또는 APP")
        DesignPlatform platform,

        @JsonProperty(required = true)
        @JsonPropertyDescription("명세 행과 시안 레이어를 어떻게 맞춰 보았는지 먼저 쓴다")
        String evidence,

        @JsonProperty(required = true)
        @JsonPropertyDescription("이 항목이 묶은 C행 번호(C1, C2 등). 같은 화면 영역의 행끼리 묶는다")
        List<String> specRowIds,

        @JsonProperty(required = true)
        @JsonPropertyDescription("행위. 예: 로그인 버튼 영역 시안이 명세와 같다")
        String title,

        @JsonProperty(required = true)
        @JsonPropertyDescription("설명. 무엇을 비교하는지 쓴다. 예: 구글, 카카오, 네이버 버튼의 순서, 문구, 아이콘이 명세와 같은지 확인한다")
        String expected,

        @JsonProperty(required = true)
        @JsonPropertyDescription("판정 근거가 된 Figma 레이어 ID(목록의 대괄호 안 값). 없으면 빈 배열")
        List<String> figmaNodeIds,

        @JsonProperty(required = true)
        @JsonPropertyDescription("판정 이유. FAIL이면 어느 행의 무엇이 어떻게 다른지 쓴다")
        String reason,

        @JsonProperty(required = true)
        @JsonPropertyDescription("PASS(명세의 요소, 문구, 순서가 시안에 모두 있음), FAIL(빠졌거나 다름), UNKNOWN(레이어 정보로 판단할 수 없음)")
        Verdict verdict
) {
}
