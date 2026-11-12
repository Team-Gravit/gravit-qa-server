package gravit.qa.designReview.dto.response;

import gravit.qa.checklist.domain.CellSource;
import gravit.qa.checklist.domain.CellState;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.domain.DesignReviewItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DesignReviewItemResponse(
        @Schema(
                description = "디자인 검수 항목 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long id,

        @Schema(
                description = "기능 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long featureId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        DesignPlatform platform,

        @Schema(
                description = "행위",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String title,

        @Schema(
                description = "설명(비교 내용과 명세 행 번호)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String expected,

        @Schema(
                description = "묶은 명세 C행 번호",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<String> specRowIds,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CellState state,

        @Schema(
                description = "AUTO면 자동 비교, MANUAL이면 사람이 바꾸거나 항목 수정으로 초기화한 결과",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        CellSource source,

        @Schema(description = "자동 비교 사유 또는 판정 초기화 사유. 사람이 상태를 바꾸면 null")
        String reason,

        @Schema(
                description = "지금 사람이 바꿀 수 있는지",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean editable,

        @Schema(
                description = "낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int version
) {

    public static DesignReviewItemResponse of(
            DesignReviewItem item,
            boolean editable
    ) {
        return new DesignReviewItemResponse(
                item.getId(),
                item.getFeatureId(),
                item.getPlatform(),
                item.getTitle(),
                item.getExpected(),
                List.copyOf(item.getSpecRowIds()),
                item.getState(),
                item.getSource(),
                item.getReason(),
                editable,
                item.getVersion()
        );
    }
}
