package gravit.qa.adapter.notion.support;

import com.fasterxml.jackson.databind.JsonNode;
import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotionBlockRenderer {

    private static final String INDENT = "  ";
    private static final String LINE_BREAK = "\n";
    private static final String NUMBERED_LIST_ITEM = "numbered_list_item";
    private static final String TABLE = "table";
    private static final String TABLE_ROW = "table_row";
    private static final String CHILD_PAGE = "child_page";
    private static final String CHILD_DATABASE = "child_database";
    private static final Set<String> MEDIA_TYPES = Set.of("image", "video", "file", "pdf", "embed", "bookmark");
    private static final String RICH_TEXT_FIELD = "rich_text";
    private static final String PLAIN_TEXT_FIELD = "plain_text";
    private static final String HREF_FIELD = "href";

    public static String toMarkdown(List<NotionBlockDto> blocks) {
        List<String> lines = new ArrayList<>();
        render(blocks, "", lines);
        return String.join(LINE_BREAK, lines);
    }

    private static void render(
            List<NotionBlockDto> blocks,
            String indent,
            List<String> lines
    ) {
        int numberedIndex = 0;
        for (NotionBlockDto block : blocks) {
            String type = block.type();
            numberedIndex = NUMBERED_LIST_ITEM.equals(type) ? numberedIndex + 1 : 0;

            if (TABLE.equals(type)) {
                renderTable(block, indent, lines);
                continue;
            }

            String line = renderLine(block, indent, numberedIndex);
            if (line != null && !line.isEmpty()) {
                lines.add(line);
            }
            if (!CHILD_PAGE.equals(type) && !CHILD_DATABASE.equals(type)) {
                render(block.children(), indent + INDENT, lines);
            }
        }
    }

    private static String renderLine(
            NotionBlockDto block,
            String indent,
            int numberedIndex
    ) {
        String type = block.type();
        JsonNode content = block.content();
        String text = richText(content.path(RICH_TEXT_FIELD));

        return switch (type) {
            case "paragraph" -> indent + text;
            case "heading_1" -> indent + "# " + text;
            case "heading_2" -> indent + "## " + text;
            case "heading_3" -> indent + "### " + text;
            case "bulleted_list_item" -> indent + "- " + text;
            case NUMBERED_LIST_ITEM -> indent + numberedIndex + ". " + text;
            case "to_do" -> indent + "- [" + (content.path("checked").asBoolean() ? "x" : " ") + "] " + text;
            case "toggle" -> indent + "▸ " + text;
            case "code" -> indent + "```" + content.path("language").asText() + LINE_BREAK + text + LINE_BREAK + indent + "```";
            case "quote" -> indent + "> " + text;
            case "callout" -> indent + "> 💡 " + text;
            case "divider" -> indent + "---";
            case CHILD_PAGE -> indent + "📄 하위 페이지: " + content.path("title").asText() + " (id: " + block.block().path("id").asText() + ")";
            case CHILD_DATABASE -> indent + "🗃 하위 데이터베이스: " + content.path("title").asText() + " (id: " + block.block().path("id").asText() + ")";
            default -> MEDIA_TYPES.contains(type) ? (indent + "[" + type + "] " + mediaUrl(content)).stripTrailing() : textOrNull(indent, text);
        };
    }

    private static void renderTable(
            NotionBlockDto table,
            String indent,
            List<String> lines
    ) {
        boolean header = true;
        for (NotionBlockDto row : table.children()) {
            if (!TABLE_ROW.equals(row.type())) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            row.content().path("cells").forEach(cell -> cells.add(richText(cell)));
            lines.add(indent + "| " + String.join(" | ", cells) + " |");
            if (header) {
                lines.add(indent + "|" + "---|".repeat(cells.size()));
                header = false;
            }
        }
    }

    private static String richText(JsonNode items) {
        StringBuilder builder = new StringBuilder();
        items.forEach(item -> {
            String text = item.path(PLAIN_TEXT_FIELD).asText();
            String href = item.path(HREF_FIELD).asText(null);
            builder.append(href == null ? text : "[" + text + "](" + href + ")");
        });
        return builder.toString();
    }

    private static String mediaUrl(JsonNode content) {
        String external = content.path("external").path("url").asText(null);
        if (external != null) {
            return external;
        }
        String file = content.path("file").path("url").asText(null);
        return file != null ? file : content.path("url").asText("");
    }

    private static String textOrNull(
            String indent,
            String text
    ) {
        return text.isEmpty() ? null : indent + text;
    }
}
