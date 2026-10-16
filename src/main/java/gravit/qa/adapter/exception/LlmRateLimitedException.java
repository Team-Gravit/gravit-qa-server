package gravit.qa.adapter.exception;

import gravit.qa.adapter.domain.ExternalApi;

public class LlmRateLimitedException extends NonRetryableExternalApiException {

    private static final int STATUS_TOO_MANY_REQUESTS = 429;

    public LlmRateLimitedException(int consecutiveCount) {
        super(
                ExternalApi.LLM,
                STATUS_TOO_MANY_REQUESTS,
                "LLM 호출이 연속 " + consecutiveCount + "회 사용 한도(429)에 걸려 중단했습니다."
        );
    }
}
