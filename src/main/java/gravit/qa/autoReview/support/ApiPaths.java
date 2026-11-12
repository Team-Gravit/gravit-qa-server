package gravit.qa.autoReview.support;

import gravit.qa.autoReview.dto.internal.ApiEndpointDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    private static final Pattern API_LINE = Pattern.compile("^(GET|POST|PUT|PATCH|DELETE)\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]*}");
    private static final Pattern MULTI_SLASH = Pattern.compile("/{2,}");
    private static final String SLASH = "/";
    private static final String VARIABLE = "{}";

    public static Optional<ApiEndpointDto> parse(String relatedApi) {
        Matcher matcher = API_LINE.matcher(relatedApi == null ? "" : relatedApi.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ApiEndpointDto(matcher.group(1).toUpperCase(Locale.ROOT), normalize(matcher.group(2))));
    }

    public static List<ApiEndpointDto> parseAll(List<String> relatedApis) {
        return relatedApis.stream()
                .map(ApiPaths::parse)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    public static String normalize(String path) {
        String value = path == null ? "" : path.trim();
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        if (!value.startsWith(SLASH)) {
            value = SLASH + value;
        }
        value = MULTI_SLASH.matcher(value).replaceAll(SLASH);
        value = PATH_VARIABLE.matcher(value).replaceAll(VARIABLE);
        if (value.length() > 1 && value.endsWith(SLASH)) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static String join(
            String prefix,
            String path
    ) {
        return normalize((prefix == null ? "" : prefix) + SLASH + (path == null ? "" : path));
    }
}
