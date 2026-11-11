package gravit.qa.adapter.figma.support;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FigmaOutlineRenderer {

    private static final String LINE_BREAK = "\n";
    private static final String INDENT = "  ";
    private static final String TEXT_LINE_BREAK = "⏎";
    private static final String TEXT = "TEXT";
    private static final String INSTANCE = "INSTANCE";
    private static final String COMPONENT = "COMPONENT";
    private static final String SOLID = "SOLID";
    private static final String IMAGE = "IMAGE";
    private static final String CHILDREN = "children";
    private static final String BOX = "absoluteBoundingBox";
    private static final Set<String> CONTAINER_TYPES = Set.of("FRAME", "GROUP", "SECTION", "COMPONENT_SET", INSTANCE, COMPONENT);
    private static final Pattern DEFAULT_NAME = Pattern.compile("^(Frame|Group|Rectangle|Vector|Ellipse|Line|Union|Subtract|Intersect|Exclude|Polygon|Star|Auto Layout)( \\d+)?$");
    private static final int MAX_DEPTH = 30;

    public static String render(JsonNode frame) {
        double originX = frame.path(BOX).path("x").asDouble();
        double originY = frame.path(BOX).path("y").asDouble();

        List<String> lines = new ArrayList<>();
        lines.add("### 프레임 " + frame.path("name").asText() + " [" + frame.path("id").asText() + "] "
                + round(frame.path(BOX).path("width").asDouble()) + "x" + round(frame.path(BOX).path("height").asDouble()));
        renderChildren(frame, originX, originY, "", lines, 0);
        return String.join(LINE_BREAK, lines);
    }

    private static void renderChildren(
            JsonNode parent,
            double originX,
            double originY,
            String indent,
            List<String> lines,
            int depth
    ) {
        if (depth >= MAX_DEPTH) {
            return;
        }
        List<JsonNode> children = new ArrayList<>();
        parent.path(CHILDREN).forEach(children::add);
        children.sort(Comparator
                .comparingDouble((JsonNode child) -> child.path(BOX).path("y").asDouble())
                .thenComparingDouble(child -> child.path(BOX).path("x").asDouble()));

        for (JsonNode child : children) {
            if (!child.path("visible").asBoolean(true)) {
                continue;
            }
            String line = lineOf(child, originX, originY);
            if (line == null) {
                renderChildren(child, originX, originY, indent, lines, depth + 1);
                continue;
            }
            lines.add(indent + "- " + line);
            renderChildren(child, originX, originY, indent + INDENT, lines, depth + 1);
        }
    }

    private static String lineOf(
            JsonNode node,
            double originX,
            double originY
    ) {
        String type = node.path("type").asText();
        String name = node.path("name").asText().trim();
        String id = "[" + node.path("id").asText() + "] ";
        String box = box(node, originX, originY);

        if (TEXT.equals(type)) {
            String text = node.path("characters").asText().replace("\r", "").replace(LINE_BREAK, TEXT_LINE_BREAK);
            String style = textStyle(node);
            return id + "글자 \"" + text + "\" " + box + style;
        }
        if (INSTANCE.equals(type) || COMPONENT.equals(type)) {
            return id + "컴포넌트 " + name + " " + box + fill(node);
        }
        if (CONTAINER_TYPES.contains(type)) {
            return DEFAULT_NAME.matcher(name).matches() ? null : id + "묶음 " + name + " " + box + fill(node);
        }
        if (hasImageFill(node)) {
            return id + "이미지 " + name + " " + box;
        }
        return DEFAULT_NAME.matcher(name).matches() ? null : id + "도형 " + name + " " + box + fill(node);
    }

    private static String box(
            JsonNode node,
            double originX,
            double originY
    ) {
        JsonNode box = node.path(BOX);
        if (box.isMissingNode() || box.isNull()) {
            return "";
        }
        return "(x=" + round(box.path("x").asDouble() - originX) + ", y=" + round(box.path("y").asDouble() - originY)
                + ", " + round(box.path("width").asDouble()) + "x" + round(box.path("height").asDouble()) + ")";
    }

    private static String textStyle(JsonNode node) {
        StringBuilder style = new StringBuilder();
        JsonNode fontSize = node.path("style").path("fontSize");
        if (fontSize.isNumber()) {
            style.append(" ").append(round(fontSize.asDouble())).append("px");
        }
        JsonNode fontWeight = node.path("style").path("fontWeight");
        if (fontWeight.isNumber()) {
            style.append(" 굵기 ").append(fontWeight.asInt());
        }
        return style + fill(node);
    }

    private static String fill(JsonNode node) {
        for (JsonNode fill : node.path("fills")) {
            if (SOLID.equals(fill.path("type").asText()) && fill.path("visible").asBoolean(true)) {
                JsonNode color = fill.path("color");
                return " " + String.format(Locale.ROOT, "#%02X%02X%02X",
                        channel(color.path("r").asDouble()),
                        channel(color.path("g").asDouble()),
                        channel(color.path("b").asDouble()));
            }
        }
        return "";
    }

    private static boolean hasImageFill(JsonNode node) {
        for (JsonNode fill : node.path("fills")) {
            if (IMAGE.equals(fill.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    private static int channel(double value) {
        return (int) Math.round(Math.max(0, Math.min(1, value)) * 255);
    }

    private static long round(double value) {
        return Math.round(value);
    }
}
