package gravit.qa.checklist.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record ChecklistDraftOutputDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("화면 1개의 체크 항목. 1개 이상")
        List<ChecklistDraftItemOutputDto> items
) {
}
