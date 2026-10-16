package gravit.qa.adapter.openapi.infrastructure;

import gravit.qa.adapter.exception.ExternalApiException;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.openapi.dto.internal.OpenApiDocumentDto;
import gravit.qa.adapter.openapi.dto.internal.OpenApiExcerptDto;
import gravit.qa.job.support.NonRetryableJobException;
import gravit.qa.support.HttpStubServer;
import gravit.qa.support.QaSpringBootTest;
import gravit.qa.support.RecordingSleeper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static gravit.qa.support.ExternalApiStubConfig.OPENAPI_PATH;
import static gravit.qa.support.HttpStubServer.StubResponse.json;
import static gravit.qa.support.HttpStubServer.StubResponse.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QaSpringBootTest
class OpenApiClientIntegrationTest {

    private static final String API_DOCS = """
            {"openapi": "3.1.0", "paths": {"/api/v1/version": {"get": {"operationId": "getVersion", "responses": {"200": {"description": "ok"}}}}}}
            """;

    @Autowired
    private OpenApiClient openApiClient;

    @Autowired
    private HttpStubServer externalApiStubServer;

    @Autowired
    private RecordingSleeper recordingSleeper;

    @Nested
    @DisplayName("dev api-docs를 받을 때")
    class 수집 {

        @Test
        void 문서를_받아_지정한_operationId만_추출한다() {
            // given
            externalApiStubServer.stub("GET", OPENAPI_PATH, json(200, API_DOCS));

            // when
            OpenApiDocumentDto apiDocs = openApiClient.fetchApiDocs();
            OpenApiExcerptDto excerpt = openApiClient.extract(apiDocs, List.of("getVersion"));

            // then
            assertThat(apiDocs.rawJson()).contains("\"openapi\"");
            assertThat(excerpt.foundOperationIds()).containsExactly("getVersion");
            assertThat(externalApiStubServer.requests().getFirst().header("Accept")).isEqualTo("application/json");
        }
    }

    @Nested
    @DisplayName("재시도 규칙에 따라")
    class 재시도 {

        @Test
        void 제한_시간_안에_응답하지_않으면_백오프로_재시도한_뒤_재시도_가능한_실패() {
            // given
            externalApiStubServer.stub("GET", OPENAPI_PATH, json(200, API_DOCS).delayed(Duration.ofSeconds(1)));

            // when & then
            assertThatThrownBy(() -> openApiClient.fetchApiDocs())
                    .isInstanceOf(ExternalApiException.class)
                    .isNotInstanceOf(NonRetryableJobException.class);
            assertThat(externalApiStubServer.requests()).hasSize(4);
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
        }

        @Test
        void 타임아웃_뒤_응답하면_성공한다() {
            // given
            externalApiStubServer.stub("GET", OPENAPI_PATH, json(200, API_DOCS).delayed(Duration.ofSeconds(1)), json(200, API_DOCS));

            // when
            OpenApiDocumentDto apiDocs = openApiClient.fetchApiDocs();

            // then
            assertThat(apiDocs.document().path("openapi").asText()).isEqualTo("3.1.0");
            assertThat(recordingSleeper.sleeps()).containsExactly(Duration.ofSeconds(1));
        }

        @Test
        void 클라이언트_오류_4xx는_재시도하지_않는다() {
            // given
            externalApiStubServer.stub("GET", OPENAPI_PATH, status(404));

            // when & then
            assertThatThrownBy(() -> openApiClient.fetchApiDocs())
                    .isInstanceOf(NonRetryableExternalApiException.class);
            assertThat(externalApiStubServer.requests()).hasSize(1);
        }
    }
}
