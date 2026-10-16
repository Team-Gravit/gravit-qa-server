package gravit.qa.adapter.llm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.config.AdapterRetryProps;
import gravit.qa.adapter.exception.ExternalApiException;
import gravit.qa.adapter.exception.LlmOutputInvalidException;
import gravit.qa.adapter.exception.LlmRateLimitedException;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.llm.config.LlmProps;
import gravit.qa.adapter.llm.dto.internal.LlmPromptDto;
import gravit.qa.adapter.llm.support.LlmCallGuard;
import gravit.qa.adapter.llm.support.LlmOutputValidator;
import gravit.qa.job.support.NonRetryableJobException;
import gravit.qa.support.HttpStubServer;
import gravit.qa.support.HttpStubServer.RecordedRequest;
import gravit.qa.support.HttpStubServer.StubResponse;
import gravit.qa.support.QaSpringBootTest;
import gravit.qa.support.RecordingSleeper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static gravit.qa.support.HttpStubServer.StubResponse.json;
import static gravit.qa.support.HttpStubServer.StubResponse.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
class LlmClientIntegrationTest {

    private static final String COMPLETIONS_PATH = "/v1/chat/completions";
    private static final LlmPromptDto PROMPT = new LlmPromptDto("너는 QA 체크리스트 작성자다.", "L.7.1 화면의 체크 항목 초안을 만들어라.");
    private static final String VALID_OUTPUT = "{\"title\": \"유닛 상세 진입\", \"parts\": [\"WEB\"]}";
    private static final Set<String> KNOWN_PARTS = Set.of("SERVER", "WEB", "APP");

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private HttpStubServer externalApiStubServer;

    @Autowired
    private RecordingSleeper recordingSleeper;

    @Autowired
    private LlmCallGuard llmCallGuard;

    @Autowired
    private LlmOutputValidator llmOutputValidator;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @Autowired
    private LlmProps llmProps;

    @Autowired
    private AdapterRetryProps adapterRetryProps;

    @Nested
    @DisplayName("구조화 출력을 요청할 때")
    class 요청 {

        @Test
        void 출력_스키마를_프롬프트에_붙여_보내고_응답을_DTO로_변환한다() throws Exception {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, completion(VALID_OUTPUT));

            // when
            DraftItemDto result = llmClient.generate(PROMPT, DraftItemDto.class);

            // then
            RecordedRequest request = externalApiStubServer.requests().getFirst();
            JsonNode body = objectMapper.readTree(request.body());
            assertSoftly(softly -> {
                softly.assertThat(result).isEqualTo(new DraftItemDto("유닛 상세 진입", List.of("WEB")));
                softly.assertThat(request.header("Authorization")).isEqualTo("Bearer test-llm-key");
                softly.assertThat(body.path("model").asText()).isEqualTo("codex-terra");
                softly.assertThat(body.at("/messages/0/role").asText()).isEqualTo("system");
                softly.assertThat(body.at("/messages/1/content").asText()).contains("JSON Schema", "\"parts\"");
            });
        }

        @Test
        void 키가_없으면_호출하지_않고_재시도하지_않는_실패() {
            // given
            LlmProps noKeyProps = new LlmProps(llmProps.baseUrl(), "", llmProps.model(), llmProps.timeout(),
                    llmProps.maxRequestsPerMinute(), llmProps.maxOutputRetries(), llmProps.maxConsecutiveRateLimits());
            LlmClient noKeyClient = new LlmClient(RestClient.builder(), llmCallGuard, llmOutputValidator, recordingSleeper, clock, noKeyProps, adapterRetryProps);

            // when & then
            assertThatThrownBy(() -> noKeyClient.generate(PROMPT, DraftItemDto.class))
                    .isInstanceOf(NonRetryableExternalApiException.class)
                    .hasMessageContaining("LITELLM_API_KEY");
            assertThat(externalApiStubServer.requests()).isEmpty();
        }

        @Test
        void 코드_블록으로_감싼_JSON도_받아들인다() {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, completion("```json\n" + VALID_OUTPUT + "\n```"));

            // when
            DraftItemDto result = llmClient.generate(PROMPT, DraftItemDto.class);

            // then
            assertThat(result.title()).isEqualTo("유닛 상세 진입");
        }
    }

    @Nested
    @DisplayName("출력이 계약을 어기면")
    class 출력_검증 {

        @Test
        void 스키마_오류를_붙여_다시_요청한다() throws Exception {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, completion("{\"title\": \"누락\"}"), completion(VALID_OUTPUT));

            // when
            DraftItemDto result = llmClient.generate(PROMPT, DraftItemDto.class);

            // then
            List<RecordedRequest> requests = externalApiStubServer.requests();
            JsonNode retryMessages = objectMapper.readTree(requests.get(1).body()).path("messages");
            assertSoftly(softly -> {
                softly.assertThat(result.parts()).containsExactly("WEB");
                softly.assertThat(requests).hasSize(2);
                softly.assertThat(retryMessages).hasSize(4);
                softly.assertThat(retryMessages.get(2).path("role").asText()).isEqualTo("assistant");
                softly.assertThat(retryMessages.get(2).path("content").asText()).isEqualTo("{\"title\": \"누락\"}");
                softly.assertThat(retryMessages.get(3).path("content").asText()).contains("출력 계약", "parts");
            });
        }

        @Test
        void 재요청_2회_후에도_어기면_재시도하지_않는_실패() {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, completion("JSON이 아닌 답변"));

            // when & then
            assertThatThrownBy(() -> llmClient.generate(PROMPT, DraftItemDto.class))
                    .isInstanceOf(LlmOutputInvalidException.class)
                    .isInstanceOf(NonRetryableJobException.class)
                    .satisfies(e -> assertThat(((LlmOutputInvalidException) e).getErrors()).isNotEmpty());
            assertThat(externalApiStubServer.requests()).hasSize(3);
        }

        @Test
        void 호출자가_준_의미_검증_오류도_붙여_다시_요청한다() throws Exception {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH,
                    completion("{\"title\": \"화면 진입\", \"parts\": [\"IOS\"]}"),
                    completion(VALID_OUTPUT));

            // when
            DraftItemDto result = llmClient.generate(PROMPT, DraftItemDto.class, LlmClientIntegrationTest::unknownParts);

            // then
            JsonNode retryMessages = objectMapper.readTree(externalApiStubServer.requests().get(1).body()).path("messages");
            assertThat(result.parts()).containsExactly("WEB");
            assertThat(retryMessages.get(3).path("content").asText()).contains("알 수 없는 파트: IOS");
        }
    }

    @Nested
    @DisplayName("호출 보호 규칙에 따라")
    class 호출_보호 {

        @Test
        void 사용_한도_429가_한_번이면_Retry_After만큼_기다린_뒤_재시도한다() {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, status(429).withHeader("Retry-After", "2"), completion(VALID_OUTPUT));

            // when
            llmClient.generate(PROMPT, DraftItemDto.class);

            // then
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(2));
            assertThat(externalApiStubServer.requests()).hasSize(2);
        }

        @Test
        void 사용_한도_429가_연속_2회면_더_호출하지_않고_재시도하지_않는_실패() {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, status(429));

            // when & then
            assertThatThrownBy(() -> llmClient.generate(PROMPT, DraftItemDto.class))
                    .isInstanceOf(LlmRateLimitedException.class)
                    .isInstanceOf(NonRetryableJobException.class);
            assertThat(externalApiStubServer.requests()).hasSize(2);
        }

        @Test
        void 사이에_다른_응답이_끼면_연속_429로_세지_않는다() {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, status(429), status(500), status(429), completion(VALID_OUTPUT));

            // when
            DraftItemDto result = llmClient.generate(PROMPT, DraftItemDto.class);

            // then
            assertThat(result.title()).isEqualTo("유닛 상세 진입");
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
        }

        @ParameterizedTest
        @ValueSource(ints = {401, 403})
        void 키_오류는_재시도하지_않고_키를_메시지에_남기지_않는다(int status) {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, status(status));

            // when & then
            assertThatThrownBy(() -> llmClient.generate(PROMPT, DraftItemDto.class))
                    .isInstanceOf(NonRetryableExternalApiException.class)
                    .hasMessageNotContaining("test-llm-key")
                    .extracting(e -> ((NonRetryableExternalApiException) e).getStatus())
                    .isEqualTo(status);
            assertThat(externalApiStubServer.requests()).hasSize(1);
        }

        @Test
        void 서버_오류는_백오프로_3회_재시도한_뒤_재시도_가능한_실패() {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, status(503));

            // when & then
            assertThatThrownBy(() -> llmClient.generate(PROMPT, DraftItemDto.class))
                    .isInstanceOf(ExternalApiException.class)
                    .isNotInstanceOf(NonRetryableJobException.class);
            assertThat(externalApiStubServer.requests()).hasSize(4);
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
        }

        @Test
        void 분당_5회를_넘으면_1분이_지날_때까지_기다린다() {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, completion(VALID_OUTPUT));

            // when
            for (int i = 0; i < 6; i++) {
                llmClient.generate(PROMPT, DraftItemDto.class);
            }

            // then
            assertThat(externalApiStubServer.requests()).hasSize(6);
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofMinutes(1));
        }

        @Test
        void 동시에_요청해도_한_번에_하나씩만_호출한다() throws Exception {
            // given
            externalApiStubServer.stub("POST", COMPLETIONS_PATH, completion(VALID_OUTPUT).delayed(Duration.ofMillis(150)));
            ExecutorService executor = Executors.newFixedThreadPool(2);

            // when
            List<Future<DraftItemDto>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> llmClient.generate(PROMPT, DraftItemDto.class)));
            }
            for (Future<DraftItemDto> future : futures) {
                future.get();
            }
            executor.shutdown();

            // then
            assertThat(externalApiStubServer.requests()).hasSize(2);
            assertThat(externalApiStubServer.maxConcurrency()).isEqualTo(1);
        }
    }

    private StubResponse completion(String content) {
        ObjectNode root = objectMapper.createObjectNode()
                .put("id", "chatcmpl-test")
                .put("object", "chat.completion")
                .put("created", 1760000000)
                .put("model", "codex-terra");
        ObjectNode choice = root.putArray("choices").addObject()
                .put("index", 0)
                .put("finish_reason", "stop");
        choice.putObject("message")
                .put("role", "assistant")
                .put("content", content);
        root.putObject("usage")
                .put("prompt_tokens", 1)
                .put("completion_tokens", 1)
                .put("total_tokens", 2);
        return json(200, root.toString());
    }

    private static List<String> unknownParts(DraftItemDto draft) {
        return draft.parts().stream()
                .filter(part -> !KNOWN_PARTS.contains(part))
                .map(part -> "알 수 없는 파트: " + part)
                .toList();
    }

    public record DraftItemDto(
            String title,

            List<String> parts
    ) {
    }
}
