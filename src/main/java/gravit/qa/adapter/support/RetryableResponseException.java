package gravit.qa.adapter.support;

import lombok.Getter;

import java.time.Duration;

@Getter
public class RetryableResponseException extends RuntimeException {

    private final int status;
    private final Duration retryAfter;

    public RetryableResponseException(
            int status,
            Duration retryAfter
    ) {
        super("재시도할 수 있는 응답입니다. status=" + status);
        this.status = status;
        this.retryAfter = retryAfter;
    }
}
