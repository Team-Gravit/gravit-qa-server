package gravit.qa.adapter.notion.support;

import gravit.qa.adapter.domain.ExternalApi;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotionIds {

    private static final Pattern ID = Pattern.compile(
            "[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}"
    );
    private static final Pattern URL_SUFFIX = Pattern.compile("[?#].*$");
    private static final String DASH = "-";

    public static String normalize(String idOrUrl) {
        String target = idOrUrl == null ? "" : URL_SUFFIX.matcher(idOrUrl.trim()).replaceFirst("");
        Matcher matcher = ID.matcher(target);
        String found = null;
        while (matcher.find()) {
            found = matcher.group();
        }
        if (found == null) {
            throw new NonRetryableExternalApiException(ExternalApi.NOTION, null, "Notion 페이지 ID를 찾을 수 없습니다: " + idOrUrl);
        }
        return found.replace(DASH, "").toLowerCase(Locale.ROOT);
    }

    public static boolean isSame(
            String left,
            String right
    ) {
        return left != null && right != null
                && left.replace(DASH, "").equalsIgnoreCase(right.replace(DASH, ""));
    }
}
