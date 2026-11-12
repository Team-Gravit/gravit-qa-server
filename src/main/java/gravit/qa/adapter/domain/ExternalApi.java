package gravit.qa.adapter.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExternalApi {

    GITHUB("GitHub"),
    NOTION("Notion"),
    FIGMA("Figma"),
    OPENAPI("dev OpenAPI"),
    LLM("LLM");

    private final String label;
}
