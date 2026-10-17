package gravit.qa.checklist.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import gravit.qa.feature.domain.Part;
import org.springframework.lang.Nullable;

import java.util.List;

@JsonPropertyOrder({"evidence", "title", "expected", "parts", "criteria", "personaKey", "testRef"})
public record ChecklistDraftItemOutputDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("이 항목의 근거. 명세, Figma 상태, OpenAPI, 변경 파일 중 무엇을 근거로 했는지 먼저 쓴다")
        String evidence,

        @JsonProperty(required = true)
        @JsonPropertyDescription("검수 항목. 한 가지 동작이나 상태만 검수한다")
        String title,

        @JsonProperty(required = true)
        @JsonPropertyDescription("기대 결과. 근거 자료에 있는 내용만 쓴다")
        String expected,

        @JsonProperty(required = true)
        @JsonPropertyDescription("적용 파트. 기능의 파트 안에서 1개 이상 고른다")
        List<Part> parts,

        @JsonProperty(required = true)
        @JsonPropertyDescription("자동화 가능 기준 5개 판정")
        ClassificationCriteriaDto criteria,

        @Nullable
        @JsonProperty(required = true)
        @JsonPropertyDescription("사전 조건 페르소나 키. 페르소나 목록에 있는 키만 쓰고, 해당 없으면 null")
        String personaKey,

        @Nullable
        @JsonProperty(required = true)
        @JsonPropertyDescription("테스트 카탈로그 ID. 카탈로그에 있는 ID만 쓰고, 없으면 null")
        String testRef
) {
}
