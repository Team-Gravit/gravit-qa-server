package gravit.qa.adapter.exception;

import gravit.qa.adapter.domain.ExternalApi;
import gravit.qa.job.support.NonRetryableJobException;
import lombok.Getter;

@Getter
public class NonRetryableExternalApiException extends NonRetryableJobException {

    private final ExternalApi api;
    private final Integer status;

    public NonRetryableExternalApiException(
            ExternalApi api,
            Integer status,
            String message
    ) {
        super(message);
        this.api = api;
        this.status = status;
    }
}
