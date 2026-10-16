package gravit.qa.adapter.notion.support;

import com.fasterxml.jackson.databind.JsonNode;
import gravit.qa.adapter.domain.ExternalApi;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpMethod;

import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotionWriteGuard {

    public static final String BASELINE_PROPERTY = "QA 기준점";

    private static final String SEARCH_PATH = "/v1/search";
    private static final Pattern DATABASE_QUERY_PATH = Pattern.compile("^/v1/databases/[0-9a-fA-F-]{32,36}/query$");
    private static final Pattern PAGE_PATH = Pattern.compile("^/v1/pages/[0-9a-fA-F-]{32,36}$");
    private static final String PROPERTIES_FIELD = "properties";

    public static void check(
            HttpMethod method,
            String path,
            JsonNode body
    ) {
        if (!isAllowed(method, path, body)) {
            throw new NonRetryableExternalApiException(ExternalApi.NOTION, null, "허용 목록에 없는 Notion 요청을 차단했습니다: " + method + " " + path);
        }
    }

    public static boolean isAllowed(
            HttpMethod method,
            String path,
            JsonNode body
    ) {
        if (HttpMethod.GET.equals(method)) {
            return true;
        }
        if (HttpMethod.POST.equals(method)) {
            return SEARCH_PATH.equals(path) || DATABASE_QUERY_PATH.matcher(path).matches();
        }
        if (HttpMethod.PATCH.equals(method)) {
            return PAGE_PATH.matcher(path).matches() && isBaselineOnly(body);
        }
        return false;
    }

    private static boolean isBaselineOnly(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 1) {
            return false;
        }
        JsonNode properties = body.get(PROPERTIES_FIELD);
        return properties != null
                && properties.isObject()
                && properties.size() == 1
                && properties.has(BASELINE_PROPERTY);
    }
}
