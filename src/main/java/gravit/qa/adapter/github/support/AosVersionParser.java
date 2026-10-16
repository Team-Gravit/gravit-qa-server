package gravit.qa.adapter.github.support;

import gravit.qa.adapter.github.dto.internal.AosVersionDto;
import gravit.qa.job.support.NonRetryableJobException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AosVersionParser {

    public static final String BUILD_GRADLE_PATH = "app/build.gradle.kts";

    private static final Pattern VERSION_CODE = Pattern.compile("^\\s*versionCode\\s*=\\s*(\\d{1,9})\\b", Pattern.MULTILINE);
    private static final Pattern VERSION_NAME = Pattern.compile("^\\s*versionName\\s*=\\s*\"([^\"]*)\"", Pattern.MULTILINE);
    private static final String VERSION_CODE_FIELD = "versionCode";
    private static final String VERSION_NAME_FIELD = "versionName";

    public static AosVersionDto parse(
            String buildGradleKts,
            Integer previousVersionCode
    ) {
        int versionCode = Integer.parseInt(find(VERSION_CODE, VERSION_CODE_FIELD, buildGradleKts));
        String versionName = find(VERSION_NAME, VERSION_NAME_FIELD, buildGradleKts);

        return new AosVersionDto(
                versionCode,
                versionName,
                previousVersionCode,
                isNotIncreased(previousVersionCode, versionCode)
        );
    }

    private static boolean isNotIncreased(
            Integer previousVersionCode,
            int versionCode
    ) {
        return previousVersionCode != null && versionCode <= previousVersionCode;
    }

    private static String find(
            Pattern pattern,
            String field,
            String buildGradleKts
    ) {
        Matcher matcher = pattern.matcher(buildGradleKts == null ? "" : buildGradleKts);
        if (!matcher.find()) {
            throw new NonRetryableJobException("gravit-aos " + BUILD_GRADLE_PATH + "에서 " + field + "를 찾을 수 없습니다.");
        }
        return matcher.group(1);
    }
}
