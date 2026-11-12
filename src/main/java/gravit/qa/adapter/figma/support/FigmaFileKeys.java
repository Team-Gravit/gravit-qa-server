package gravit.qa.adapter.figma.support;

import gravit.qa.adapter.domain.ExternalApi;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FigmaFileKeys {

    private static final Pattern FILE_KEY = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern FILE_URL = Pattern.compile("figma\\.com/(?:file|design|proto|board)/([A-Za-z0-9]+)");

    public static String fromUrl(String url) {
        Matcher matcher = FILE_URL.matcher(url == null ? "" : url);
        if (!matcher.find()) {
            throw new NonRetryableExternalApiException(ExternalApi.FIGMA, null, "Figma 파일 URL에서 파일 키를 찾을 수 없습니다: " + url);
        }
        return matcher.group(1);
    }

    public static String requireValid(String fileKey) {
        if (fileKey == null || !FILE_KEY.matcher(fileKey).matches()) {
            throw new NonRetryableExternalApiException(ExternalApi.FIGMA, null, "Figma 파일 키 형식이 아닙니다: " + fileKey);
        }
        return fileKey;
    }
}
