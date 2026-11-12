package gravit.qa.adapter.notion.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotionRichTexts {

    public static final int MAX_TEXT_LENGTH = 2000;

    private static final String PLAIN_TEXT_FIELD = "plain_text";

    public static ArrayNode of(String text) {
        ArrayNode richText = JsonNodeFactory.instance.arrayNode();
        String value = text == null ? "" : text;
        for (int start = 0; start < value.length(); start += MAX_TEXT_LENGTH) {
            richText.addObject()
                    .put("type", "text")
                    .putObject("text").put("content", value.substring(start, Math.min(value.length(), start + MAX_TEXT_LENGTH)));
        }
        return richText;
    }

    public static String plainText(JsonNode richText) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : richText) {
            if (item.has(PLAIN_TEXT_FIELD)) {
                builder.append(item.path(PLAIN_TEXT_FIELD).asText());
            } else {
                builder.append(item.path("text").path("content").asText());
            }
        }
        return builder.toString();
    }
}
