package gravit.qa.adapter.llm.support;

import gravit.qa.adapter.support.RetryAfterParser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;

@RequiredArgsConstructor
public class LlmResponseErrorHandler implements ResponseErrorHandler {

    private final Clock clock;

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isError();
    }

    @Override
    public void handleError(
            URI url,
            HttpMethod method,
            ClientHttpResponse response
    ) throws IOException {
        throw new LlmStatusException(response.getStatusCode().value(), RetryAfterParser.parse(response.getHeaders(), clock));
    }
}
