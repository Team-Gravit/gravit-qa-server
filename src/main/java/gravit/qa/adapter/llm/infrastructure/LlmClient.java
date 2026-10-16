package gravit.qa.adapter.llm.infrastructure;

import gravit.qa.adapter.config.AdapterRetryProps;
import gravit.qa.adapter.exception.ExternalApiException;
import gravit.qa.adapter.exception.LlmOutputInvalidException;
import gravit.qa.adapter.exception.LlmRateLimitedException;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.llm.config.LlmProps;
import gravit.qa.adapter.llm.dto.internal.LlmPromptDto;
import gravit.qa.adapter.llm.dto.internal.LlmValidationResultDto;
import gravit.qa.adapter.llm.support.LlmCallGuard;
import gravit.qa.adapter.llm.support.LlmOutputSchema;
import gravit.qa.adapter.llm.support.LlmOutputValidator;
import gravit.qa.adapter.llm.support.LlmResponseErrorHandler;
import gravit.qa.adapter.llm.support.LlmStatusException;
import gravit.qa.adapter.support.HttpRequestFactories;
import gravit.qa.adapter.support.Sleeper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static gravit.qa.adapter.domain.ExternalApi.LLM;

@Slf4j
@Component
public class LlmClient {

    private static final String FORMAT_SEPARATOR = "\n\n";
    private static final String CORRECTION_HEADER = "직전 응답이 출력 계약을 지키지 않았습니다. 아래 오류를 모두 고쳐 설명 없이 JSON만 다시 출력하세요.";
    private static final String ERROR_LINE_PREFIX = "\n- ";
    private static final int STATUS_TOO_MANY_REQUESTS = 429;
    private static final int STATUS_SERVER_ERROR_MIN = 500;
    private static final int SINGLE_ATTEMPT = 1;

    private final ChatModel chatModel;

    private final LlmCallGuard llmCallGuard;
    private final LlmOutputValidator llmOutputValidator;
    private final Sleeper sleeper;

    private final LlmProps llmProps;
    private final AdapterRetryProps adapterRetryProps;

    public LlmClient(
            RestClient.Builder restClientBuilder,
            LlmCallGuard llmCallGuard,
            LlmOutputValidator llmOutputValidator,
            Sleeper sleeper,
            Clock clock,
            LlmProps llmProps,
            AdapterRetryProps adapterRetryProps
    ) {
        this.chatModel = StringUtils.hasText(llmProps.apiKey())
                ? createChatModel(restClientBuilder, clock, llmProps)
                : null;
        this.llmCallGuard = llmCallGuard;
        this.llmOutputValidator = llmOutputValidator;
        this.sleeper = sleeper;
        this.llmProps = llmProps;
        this.adapterRetryProps = adapterRetryProps;
    }

    public <T> T generate(
            LlmPromptDto prompt,
            Class<T> outputType
    ) {
        return generate(prompt, outputType, value -> List.of());
    }

    public <T> T generate(
            LlmPromptDto prompt,
            Class<T> outputType,
            Function<T, List<String>> semanticValidator
    ) {
        ChatModel model = requireChatModel();
        LlmOutputSchema<T> schema = llmOutputValidator.schemaOf(outputType);

        List<Message> messages = new ArrayList<>();
        if (StringUtils.hasText(prompt.system())) {
            messages.add(new SystemMessage(prompt.system()));
        }
        messages.add(new UserMessage(prompt.user() + FORMAT_SEPARATOR + schema.format()));

        int maxAttempts = llmProps.maxOutputRetries() + 1;
        AtomicInteger consecutiveRateLimits = new AtomicInteger();
        List<String> errors = List.of();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String output = request(model, messages, consecutiveRateLimits);
            LlmValidationResultDto<T> result = llmOutputValidator.validate(output, schema, semanticValidator);
            if (result.isValid()) {
                return result.value();
            }

            errors = result.errors();
            log.warn("LLM 출력 검증 실패 {}/{}: {}", attempt, maxAttempts, errors);
            messages.add(new AssistantMessage(output == null ? "" : output));
            messages.add(new UserMessage(CORRECTION_HEADER + ERROR_LINE_PREFIX + String.join(ERROR_LINE_PREFIX, errors)));
        }
        throw new LlmOutputInvalidException(maxAttempts, errors);
    }

    private String request(
            ChatModel model,
            List<Message> messages,
            AtomicInteger consecutiveRateLimits
    ) {
        Prompt prompt = new Prompt(List.copyOf(messages));
        for (int retry = 0; ; retry++) {
            Integer status;
            Duration wait;
            try {
                String output = llmCallGuard.execute(() -> textOf(model.call(prompt)));
                consecutiveRateLimits.set(0);
                return output;
            } catch (LlmStatusException e) {
                status = e.getStatus();
                wait = waitFor(e, retry, consecutiveRateLimits);
            } catch (ResourceAccessException e) {
                status = null;
                consecutiveRateLimits.set(0);
                wait = adapterRetryProps.backoff(retry);
            }

            if (retry >= adapterRetryProps.maxRetries()) {
                throw new ExternalApiException(LLM, status, "LLM 호출이 재시도 " + adapterRetryProps.maxRetries() + "회 후에도 실패했습니다. status=" + status);
            }
            if (wait.compareTo(adapterRetryProps.maxWait()) > 0) {
                throw new ExternalApiException(LLM, status, "LLM 재시도 대기(" + wait.toSeconds() + "초)가 한도를 넘어 중단했습니다. status=" + status);
            }
            log.warn("LLM 호출 실패, {}ms 뒤 재시도 {}/{} (status={})", wait.toMillis(), retry + 1, adapterRetryProps.maxRetries(), status);
            sleeper.sleep(wait);
        }
    }

    private Duration waitFor(
            LlmStatusException exception,
            int retry,
            AtomicInteger consecutiveRateLimits
    ) {
        int status = exception.getStatus();
        if (status == STATUS_TOO_MANY_REQUESTS) {
            int consecutive = consecutiveRateLimits.incrementAndGet();
            if (consecutive >= llmProps.maxConsecutiveRateLimits()) {
                throw new LlmRateLimitedException(consecutive);
            }
            return exception.getRetryAfter() != null ? exception.getRetryAfter() : adapterRetryProps.backoff(retry);
        }

        consecutiveRateLimits.set(0);
        if (status >= STATUS_SERVER_ERROR_MIN) {
            return adapterRetryProps.backoff(retry);
        }
        throw new NonRetryableExternalApiException(LLM, status, "LLM 호출이 거부되었습니다. status=" + status);
    }

    private ChatModel requireChatModel() {
        if (chatModel == null) {
            throw new NonRetryableExternalApiException(LLM, null, "LLM 키(LITELLM_API_KEY)가 설정되지 않았습니다.");
        }
        return chatModel;
    }

    private static String textOf(ChatResponse response) {
        Generation result = response == null ? null : response.getResult();
        return result == null ? null : result.getOutput().getText();
    }

    private static ChatModel createChatModel(
            RestClient.Builder restClientBuilder,
            Clock clock,
            LlmProps llmProps
    ) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(llmProps.baseUrl())
                .apiKey(llmProps.apiKey())
                .restClientBuilder(restClientBuilder.requestFactory(HttpRequestFactories.jdk(llmProps.timeout())))
                .responseErrorHandler(new LlmResponseErrorHandler(clock))
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(llmProps.model()).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(SINGLE_ATTEMPT).build())
                .build();
    }
}
