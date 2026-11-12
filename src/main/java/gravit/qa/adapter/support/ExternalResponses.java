package gravit.qa.adapter.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.domain.ExternalApi;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExternalResponses {

    private static final int STATUS_TOO_MANY_REQUESTS = 429;
    private static final int STATUS_SERVER_ERROR_MIN = 500;

    public static String readText(
            ExternalApi api,
            String operation,
            ConvertibleClientHttpResponse response,
            Clock clock
    ) throws IOException {
        return new String(readBytes(api, operation, response, clock), StandardCharsets.UTF_8);
    }

    public static byte[] readBytes(
            ExternalApi api,
            String operation,
            ConvertibleClientHttpResponse response,
            Clock clock
    ) throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody().readAllBytes();
        }
        throw failure(api, operation, response.getStatusCode().value(), response.getHeaders(), clock);
    }

    public static RuntimeException failure(
            ExternalApi api,
            String operation,
            int status,
            HttpHeaders headers,
            Clock clock
    ) {
        if (isRetryable(status)) {
            return new RetryableResponseException(status, RetryAfterParser.parse(headers, clock));
        }
        return nonRetryable(api, operation, status);
    }

    public static boolean isRetryable(int status) {
        return status == STATUS_TOO_MANY_REQUESTS || status >= STATUS_SERVER_ERROR_MIN;
    }

    public static NonRetryableExternalApiException nonRetryable(
            ExternalApi api,
            String operation,
            int status
    ) {
        return new NonRetryableExternalApiException(api, status, describe(api, operation) + " 요청이 실패했습니다. status=" + status);
    }

    public static JsonNode parseJson(
            ObjectMapper objectMapper,
            ExternalApi api,
            String operation,
            String body
    ) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw invalidResponse(api, operation, "JSON으로 해석할 수 없습니다");
        }
    }

    public static String requireText(
            JsonNode node,
            String pointer,
            ExternalApi api,
            String operation
    ) {
        JsonNode value = node.at(pointer);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw invalidResponse(api, operation, pointer + " 값이 없습니다");
        }
        return value.asText();
    }

    public static NonRetryableExternalApiException invalidResponse(
            ExternalApi api,
            String operation,
            String reason
    ) {
        return new NonRetryableExternalApiException(api, null, describe(api, operation) + " 응답 형식이 예상과 다릅니다: " + reason);
    }

    public static String describe(
            ExternalApi api,
            String operation
    ) {
        return api.getLabel() + " " + operation;
    }
}
