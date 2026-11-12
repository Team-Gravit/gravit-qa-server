package gravit.qa.checklist.support;

import gravit.qa.global.exception.domain.RestApiException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static gravit.qa.global.exception.domain.CustomErrorCode.INVALID_PARAMS;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FailReasonPolicy {

    public static final int MAX_LENGTH = 500;

    public static String normalize(String reason) {
        if (reason == null) {
            return null;
        }
        String stripped = reason.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        if (stripped.length() > MAX_LENGTH) {
            throw new RestApiException(INVALID_PARAMS);
        }
        return stripped;
    }
}
