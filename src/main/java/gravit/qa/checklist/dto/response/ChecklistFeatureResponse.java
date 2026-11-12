package gravit.qa.checklist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ChecklistFeatureResponse(
        @Schema(
                description = "기능 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long featureId,

        @Schema(
                description = "화면 ID",
                example = "ACC-01",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String screenId,

        @Schema(
                description = "화면 이름",
                example = "로그인",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String screenName,

        @Schema(
                description = "이번 스프린트 수정 이력 행이 있는 화면",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean changed,

        @Schema(
                description = "이 화면의 셀 중 검수한 비율(0~100, NOT_APPLICABLE 제외, 버림)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int completionRate,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ChecklistItemResponse> items
) {

    public static ChecklistFeatureResponse of(
            long featureId,
            String screenId,
            String screenName,
            boolean changed,
            int completionRate,
            List<ChecklistItemResponse> items
    ) {
        return new ChecklistFeatureResponse(featureId, screenId, screenName, changed, completionRate, List.copyOf(items));
    }
}
