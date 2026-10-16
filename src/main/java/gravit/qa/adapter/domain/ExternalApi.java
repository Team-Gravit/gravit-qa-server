package gravit.qa.adapter.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExternalApi {

    GITHUB("GitHub"),
    NOTION("Notion"),
    FIGMA("Figma"),
    FIGMA_IMAGE("Figma 이미지"),
    OPENAPI("dev OpenAPI"),
    LLM("LLM");

    private final String label;
}
