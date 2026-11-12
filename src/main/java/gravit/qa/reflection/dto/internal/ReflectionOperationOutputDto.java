package gravit.qa.reflection.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import gravit.qa.reflection.domain.ReflectionOperationType;
import org.springframework.lang.Nullable;

import java.util.List;

@JsonPropertyOrder({"historyRow", "op", "target", "blockType", "text", "cells"})
public record ReflectionOperationOutputDto(
        @JsonProperty(required = true)
        @JsonPropertyDescription("이 편집이 반영하는 수정 이력 행 번호(1부터)")
        int historyRow,

        @JsonProperty(required = true)
        @JsonPropertyDescription("UPDATE_TEXT(글 블록 문장 교체), UPDATE_ROW(표 행 전체 칸 교체), INSERT_BLOCK_AFTER(대상 블록 뒤에 글 블록 추가), INSERT_ROW_AFTER(대상 행 뒤에 표 행 추가), DELETE(글 블록이나 표 행 삭제)")
        ReflectionOperationType op,

        @JsonProperty(required = true)
        @JsonPropertyDescription("대상 블록 키. 본문 블록 목록의 [b3], [t2.r4] 같은 키에서 대괄호를 뺀 값")
        String target,

        @Nullable
        @JsonProperty(required = true)
        @JsonPropertyDescription("INSERT_BLOCK_AFTER일 때 새 블록 종류(paragraph, heading_3, bulleted_list_item, numbered_list_item, to_do, quote, callout, toggle). 그 밖에는 null")
        String blockType,

        @Nullable
        @JsonProperty(required = true)
        @JsonPropertyDescription("UPDATE_TEXT, INSERT_BLOCK_AFTER일 때 블록의 새 문장 전체. 그 밖에는 null")
        String text,

        @Nullable
        @JsonProperty(required = true)
        @JsonPropertyDescription("UPDATE_ROW, INSERT_ROW_AFTER일 때 행의 모든 칸 값(왼쪽부터). 그 밖에는 null")
        List<String> cells
) {
}
