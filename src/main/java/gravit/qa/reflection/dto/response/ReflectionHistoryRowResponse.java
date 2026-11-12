package gravit.qa.reflection.dto.response;

import gravit.qa.reflection.dto.internal.ReflectionHistoryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReflectionHistoryRowResponse(
        @Schema(
                description = "수정 이력 표 '스프린트' 값",
                example = "4th Agile",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String sprint,

        @Schema(
                description = "수정 이력 표 '수정 사항' 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String change
) {

    public static ReflectionHistoryRowResponse from(ReflectionHistoryRowDto row) {
        return new ReflectionHistoryRowResponse(row.sprint(), row.change());
    }
}
