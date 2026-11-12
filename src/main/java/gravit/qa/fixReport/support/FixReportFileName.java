package gravit.qa.fixReport.support;

import gravit.qa.sprint.domain.ProgressPart;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FixReportFileName {

    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");
    private static final String SLUG_SEPARATOR = "-";
    private static final String FALLBACK_SLUG = "sprint-%d";
    private static final String FILE_NAME = "%s_%s_fixes.txt";
    private static final String CONTENT_DISPOSITION = "attachment; filename=\"%s\"; filename*=UTF-8''%s";
    private static final String RFC5987_ATTR_CHARS = "!#$&+-.^_`|~";
    private static final String PERCENT_ENCODED = "%%%02X";

    public static String of(
            long sprintId,
            String sprintKey,
            ProgressPart part
    ) {
        return FILE_NAME.formatted(slug(sprintId, sprintKey), part.name());
    }

    public static String contentDisposition(String fileName) {
        return CONTENT_DISPOSITION.formatted(fileName, encode(fileName));
    }

    static String slug(
            long sprintId,
            String sprintKey
    ) {
        String lowered = sprintKey == null ? "" : sprintKey.toLowerCase(Locale.ROOT);
        String slug = EDGE_HYPHENS.matcher(NON_SLUG_CHARS.matcher(lowered).replaceAll(SLUG_SEPARATOR)).replaceAll("");
        return slug.isEmpty() ? FALLBACK_SLUG.formatted(sprintId) : slug;
    }

    private static String encode(String value) {
        StringBuilder builder = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if (isAttrChar(c)) {
                builder.append(c);
            } else {
                builder.append(PERCENT_ENCODED.formatted(b & 0xFF));
            }
        }
        return builder.toString();
    }

    private static boolean isAttrChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || RFC5987_ATTR_CHARS.indexOf(c) >= 0;
    }
}
