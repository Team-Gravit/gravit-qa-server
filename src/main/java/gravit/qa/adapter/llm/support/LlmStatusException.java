package gravit.qa.adapter.llm.support;

import lombok.Getter;

import java.time.Duration;

@Getter
public class LlmStatusException extends RuntimeException {

    private final int status;
    private final Duration retryAfter;

    public LlmStatusException(
            int status,
            Duration retryAfter
    ) {
        super("LLM 응답 오류. status=" + status);
        this.status = status;
        this.retryAfter = retryAfter;
    }
}
