package gravit.qa.adapter.notion.support;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotionPropertyReader {

    private static final String TYPE_FIELD = "type";
    private static final String PLAIN_TEXT_FIELD = "plain_text";
    private static final String NAME_FIELD = "name";
    private static final String ID_FIELD = "id";
    private static final String START_FIELD = "start";
    private static final String END_FIELD = "end";
    private static final int ISO_DATE_LENGTH = 10;

    public static String text(JsonNode property) {
        JsonNode items = property.path(property.path(TYPE_FIELD).asText());
        if (!items.isArray()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        items.forEach(item -> builder.append(item.path(PLAIN_TEXT_FIELD).asText()));
        return builder.toString();
    }

    public static String url(JsonNode property) {
        return property.path("url").asText(null);
    }

    public static String select(JsonNode property) {
        return property.path("select").path(NAME_FIELD).asText(null);
    }

    public static List<String> multiSelect(JsonNode property) {
        List<String> names = new ArrayList<>();
        property.path("multi_select").forEach(option -> names.add(option.path(NAME_FIELD).asText()));
        return names;
    }

    public static LocalDate dateStart(JsonNode property) {
        return toDate(property.path("date").path(START_FIELD).asText(null));
    }

    public static LocalDate dateEnd(JsonNode property) {
        return toDate(property.path("date").path(END_FIELD).asText(null));
    }

    public static List<String> relationIds(JsonNode property) {
        List<String> ids = new ArrayList<>();
        property.path("relation").forEach(relation -> ids.add(NotionIds.normalize(relation.path(ID_FIELD).asText())));
        return ids;
    }

    public static boolean hasMore(JsonNode property) {
        return property.path("has_more").asBoolean(false);
    }

    private static LocalDate toDate(String value) {
        if (value == null || value.length() < ISO_DATE_LENGTH) {
            return null;
        }
        return LocalDate.parse(value.substring(0, ISO_DATE_LENGTH));
    }
}
