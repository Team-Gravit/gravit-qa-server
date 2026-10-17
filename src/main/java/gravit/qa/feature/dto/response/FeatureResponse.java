package gravit.qa.feature.dto.response;

import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;
import gravit.qa.feature.dto.internal.FeatureDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record FeatureResponse(
        @Schema(
                description = "기능 ID",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long id,

        @Schema(
                description = "화면 ID",
                example = "LRN-02",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String screenId,

        @Schema(
                description = "기능 이름",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String name,

        @Schema(description = "라우트")
        String route,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Part> parts,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SpecStatus specStatus,

        @Schema(description = "Notion 기능 명세 페이지 URL")
        String notionUrl,

        @Schema(description = "이번 스프린트 Figma 화면 프레임 node ID")
        List<String> figmaNodeIds
) {

    private static final String NOTION_PAGE_URL = "https://www.notion.so/";

    public static FeatureResponse of(
            FeatureDto feature,
            List<String> figmaNodeIds
    ) {
        return new FeatureResponse(
                feature.id(),
                feature.screenId(),
                feature.name(),
                feature.route(),
                feature.parts(),
                feature.specStatus(),
                NOTION_PAGE_URL + feature.notionPageId(),
                List.copyOf(figmaNodeIds)
        );
    }
}
