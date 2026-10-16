package gravit.qa.adapter.notion.dto.internal;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record NotionBlockDto(
        JsonNode block,

        List<NotionBlockDto> children
) {

    public String type() {
        return block.path("type").asText();
    }

    public JsonNode content() {
        return block.path(type());
    }
}
