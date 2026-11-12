package gravit.qa.adapter.support;

import gravit.qa.adapter.config.AdapterRetryProps;
import gravit.qa.adapter.domain.ExternalApi;
import gravit.qa.adapter.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalCallExecutor {

    private final Sleeper sleeper;

    private final AdapterRetryProps adapterRetryProps;

    public <T> T execute(
            ExternalApi api,
            String operation,
            Supplier<T> call
    ) {
        for (int retry = 0; ; retry++) {
            Integer status;
            Duration wait;
            try {
                return call.get();
            } catch (RetryableResponseException e) {
                status = e.getStatus();
                wait = e.getRetryAfter() != null ? e.getRetryAfter() : adapterRetryProps.backoff(retry);
            } catch (ResourceAccessException e) {
                status = null;
                wait = adapterRetryProps.backoff(retry);
            }

            awaitRetry(api, operation, status, wait, retry);
        }
    }

    private void awaitRetry(
            ExternalApi api,
            String operation,
            Integer status,
            Duration wait,
            int retry
    ) {
        String target = ExternalResponses.describe(api, operation);
        if (retry >= adapterRetryProps.maxRetries()) {
            throw new ExternalApiException(api, status, target + " 요청이 재시도 " + adapterRetryProps.maxRetries() + "회 후에도 실패했습니다. status=" + status);
        }
        if (wait.compareTo(adapterRetryProps.maxWait()) > 0) {
            throw new ExternalApiException(api, status, target + " 재시도 대기(" + wait.toSeconds() + "초)가 한도를 넘어 중단했습니다. status=" + status);
        }

        log.warn("{} 실패, {}ms 뒤 재시도 {}/{} (status={})", target, wait.toMillis(), retry + 1, adapterRetryProps.maxRetries(), status);
        sleeper.sleep(wait);
    }
}
