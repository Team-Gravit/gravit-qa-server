package gravit.qa.reflection.dto.response;

import gravit.qa.feature.dto.internal.FeatureDto;
import gravit.qa.reflection.domain.ReflectionState;
import gravit.qa.reflection.dto.internal.SpecReflectionDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ReflectionResponse(
        @Schema(
                description = "반영 제안 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long id,

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
                description = "반영할 이번 스프린트 수정 이력 행",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<ReflectionHistoryRowResponse> historyRows,

        @Schema(
                description = "바뀌기 전 본문(마크다운)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String before,

        @Schema(
                description = "바뀐 뒤 본문(마크다운)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String after,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ReflectionState state,

        @Schema(description = "실패 사유 한 줄. 정상이면 null")
        String message,

        @Schema(
                description = "낙관적 잠금 값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int version
) {

    public static ReflectionResponse of(
            SpecReflectionDto reflection,
            FeatureDto feature
    ) {
        return new ReflectionResponse(
                reflection.id(),
                reflection.featureId(),
                feature.screenId(),
                feature.name(),
                reflection.historyRows().stream().map(ReflectionHistoryRowResponse::from).toList(),
                reflection.beforeMarkdown(),
                reflection.afterMarkdown(),
                reflection.state(),
                reflection.message(),
                reflection.version()
        );
    }
}
