package gravit.qa.adapter.notion.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotionWriteGuardTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PAGE_PATH = "/v1/pages/11111111111111111111111111111111";

    @Nested
    @DisplayName("허용 목록에 있는 요청은")
    class 허용 {

        @Test
        void 조회_요청은_모두_통과한다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.GET, PAGE_PATH, null)).isTrue();
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.GET, "/v1/blocks/abc/children", null)).isTrue();
        }

        @Test
        void DB_query와_search는_통과한다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.POST, "/v1/databases/4e53389542c34a339a856820e77ccefc/query", json("{}"))).isTrue();
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.POST, "/v1/search", json("{}"))).isTrue();
        }

        @Test
        void QA_기준점_속성_하나만_갱신하는_PATCH는_통과한다() {
            // given
            JsonNode body = json("{\"properties\": {\"QA 기준점\": {\"rich_text\": []}}}");

            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, PAGE_PATH, body)).isTrue();
        }
    }

    @Nested
    @DisplayName("허용 목록 밖의 요청은")
    class 차단 {

        @Test
        void 다른_속성을_함께_바꾸는_PATCH를_막는다() {
            // given
            JsonNode body = json("{\"properties\": {\"QA 기준점\": {\"rich_text\": []}, \"이름\": {\"title\": []}}}");

            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, PAGE_PATH, body)).isFalse();
        }

        @Test
        void 속성_외_필드를_바꾸는_PATCH를_막는다() {
            // given
            JsonNode body = json("{\"properties\": {\"QA 기준점\": {\"rich_text\": []}}, \"archived\": true}");

            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, PAGE_PATH, body)).isFalse();
        }

        @Test
        void 페이지_생성과_블록_수정_삭제를_막는다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.POST, "/v1/pages", json("{}"))).isFalse();
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/11111111111111111111111111111111/children", json("{}"))).isFalse();
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.DELETE, "/v1/blocks/11111111111111111111111111111111", null)).isFalse();
        }

        @Test
        void 차단하면_재시도하지_않는_실패를_던진다() {
            // when & then
            assertThatThrownBy(() -> NotionWriteGuard.check(HttpMethod.DELETE, PAGE_PATH, null))
                    .isInstanceOf(NonRetryableExternalApiException.class)
                    .hasMessageContaining("차단");
        }
    }

    private static JsonNode json(String value) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
