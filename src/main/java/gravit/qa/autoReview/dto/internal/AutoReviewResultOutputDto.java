package gravit.qa.autoReview.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import gravit.qa.checklist.domain.Verdict;

import java.util.List;

@JsonPropertyOrder({"itemId", "evidence", "reason", "verdict"})
public record AutoReviewResultOutputDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("체크 항목 번호(입력의 [항목 n]의 n)")
        long itemId,

        @JsonProperty(required = true)
        @JsonPropertyDescription("판정 근거가 된 코드 위치. 입력 코드의 파일 경로와 줄 번호만 쓴다. 근거가 없으면 빈 배열")
        List<CodeEvidenceOutputDto> evidence,

        @JsonProperty(required = true)
        @JsonPropertyDescription("판정 이유 한두 문장. 코드가 무엇을 하는지와 기대 결과와의 차이를 쓴다")
        String reason,

        @JsonProperty(required = true)
        @JsonPropertyDescription("PASS(기대 결과를 코드에서 확인), FAIL(코드가 다르게 동작한다는 근거가 있음), UNKNOWN(관련 코드가 입력에 없거나 실행해야 알 수 있음)")
        Verdict verdict
) {
}
