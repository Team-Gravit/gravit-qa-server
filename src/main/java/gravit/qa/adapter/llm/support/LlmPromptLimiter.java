package gravit.qa.adapter.llm.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LlmPromptLimiter {

    public static final String TRUNCATION_NOTICE = "\n\n(입력 길이 상한으로 이후 내용 생략)";

    public static String limit(
            String user,
            int maxChars
    ) {
        if (user.length() <= maxChars) {
            return user;
        }
        return user.substring(0, maxChars - TRUNCATION_NOTICE.length()) + TRUNCATION_NOTICE;
    }
}
