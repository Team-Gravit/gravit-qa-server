package gravit.qa.adapter.notion.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.notion.dto.internal.NotionWriteScopeDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotionWriteGuardTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PAGE_ID = "11111111111111111111111111111111";
    private static final String PARAGRAPH_ID = "22222222222222222222222222222222";
    private static final String TABLE_ID = "33333333333333333333333333333333";
    private static final String ROW_ID = "44444444444444444444444444444444";
    private static final String HISTORY_ROW_ID = "55555555555555555555555555555555";
    private static final String OTHER_ID = "66666666666666666666666666666666";
    private static final NotionWriteScopeDto SCOPE = NotionWriteScopeDto.of(
            PAGE_ID,
            Map.of(PARAGRAPH_ID, "paragraph", ROW_ID, "table_row"),
            Map.of(ROW_ID, 2),
            Map.of(TABLE_ID, 2),
            Set.of(PAGE_ID, PARAGRAPH_ID),
            Set.of(PARAGRAPH_ID, TABLE_ID, ROW_ID),
            Map.of(HISTORY_ROW_ID, List.of("4th Agile", "로그인 버튼에 Apple 추가", "")),
            2
    );

    @Nested
    @DisplayName("범위 없이 보내는 요청은")
    class 범위_없음 {

        @Test
        void 조회와_DB_query_search만_통과한다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.GET, "/v1/pages/" + PAGE_ID, null, null)).isTrue();
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.POST, "/v1/databases/4e53389542c34a339a856820e77ccefc/query", json("{}"), null)).isTrue();
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.POST, "/v1/search", json("{}"), null)).isTrue();
        }

        @Test
        void 스프린트_페이지_QA_기준점_속성_쓰기도_막는다() {
            // given
            JsonNode body = json("{\"properties\": {\"QA 기준점\": {\"rich_text\": []}}}");

            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/pages/" + PAGE_ID, body, null)).isFalse();
        }

        @Test
        void 블록_쓰기를_막고_재시도하지_않는_실패로_던진다() {
            // when & then
            assertThatThrownBy(() -> NotionWriteGuard.check(HttpMethod.PATCH, "/v1/blocks/" + PARAGRAPH_ID, textBody("paragraph", "바뀐 문장"), null))
                    .isInstanceOf(NonRetryableExternalApiException.class)
                    .hasMessageContaining("허용 목록에 없는 Notion 요청");
        }
    }

    @Nested
    @DisplayName("대상 기능 페이지 범위 안에서는")
    class 범위_안 {

        @Test
        void 글_블록의_문장을_같은_종류로_바꾸는_요청을_통과시킨다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + PARAGRAPH_ID, textBody("paragraph", "바뀐 문장"), SCOPE)).isTrue();
        }

        @Test
        void 열_수가_맞는_표_행_수정을_통과시킨다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + ROW_ID, rowBody("C8", "Apple 버튼"), SCOPE)).isTrue();
        }

        @Test
        void 페이지나_허용된_블록_뒤에_글_블록을_추가하는_요청을_통과시킨다() {
            // given
            JsonNode body = json("{\"children\": [{\"type\": \"paragraph\", \"paragraph\": {\"rich_text\": []}}], \"after\": \"" + PARAGRAPH_ID + "\"}");

            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + PAGE_ID + "/children", body, SCOPE)).isTrue();
        }

        @Test
        void 표에_열_수가_맞는_행을_추가하는_요청을_통과시킨다() {
            // given
            JsonNode body = json("{\"children\": [{\"type\": \"table_row\", \"table_row\": {\"cells\": [[], []]}}], \"after\": \"" + ROW_ID + "\"}");

            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + TABLE_ID + "/children", body, SCOPE)).isTrue();
        }

        @Test
        void 허용된_블록_삭제를_통과시킨다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.DELETE, "/v1/blocks/" + PARAGRAPH_ID, null, SCOPE)).isTrue();
        }

        @Test
        void 수정_이력_행은_반영일_칸만_날짜로_채우는_요청을_통과시킨다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + HISTORY_ROW_ID,
                    rowBody("4th Agile", "로그인 버튼에 Apple 추가", "2026-11-12"), SCOPE)).isTrue();
        }
    }

    @Nested
    @DisplayName("범위를 벗어난 요청은")
    class 범위_밖 {

        @Test
        void 범위에_없는_블록_수정과_삭제를_막는다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + OTHER_ID, textBody("paragraph", "x"), SCOPE)).isFalse();
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.DELETE, "/v1/blocks/" + OTHER_ID, null, SCOPE)).isFalse();
        }

        @Test
        void 블록_종류를_바꾸는_수정을_막는다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + PARAGRAPH_ID, textBody("heading_3", "제목"), SCOPE)).isFalse();
        }

        @Test
        void 보관이나_휴지통_이동을_PATCH로_보내면_막는다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + PARAGRAPH_ID, json("{\"archived\": true}"), SCOPE)).isFalse();
        }

        @Test
        void 열_수가_다른_표_행을_막는다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + ROW_ID, rowBody("C8"), SCOPE)).isFalse();
        }

        @Test
        void 수정_이력_행의_스프린트나_수정_사항을_바꾸면_막는다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + HISTORY_ROW_ID,
                    rowBody("4th Agile", "다른 내용", "2026-11-12"), SCOPE)).isFalse();
        }

        @Test
        void 반영일이_날짜_형식이_아니면_막는다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + HISTORY_ROW_ID,
                    rowBody("4th Agile", "로그인 버튼에 Apple 추가", "어제"), SCOPE)).isFalse();
        }

        @Test
        void 허용되지_않은_부모에_블록을_추가하거나_하위_블록을_함께_보내면_막는다() {
            // given
            JsonNode otherParent = json("{\"children\": [{\"type\": \"paragraph\", \"paragraph\": {\"rich_text\": []}}]}");
            JsonNode nested = json("{\"children\": [{\"type\": \"toggle\", \"toggle\": {\"rich_text\": [], \"children\": []}}]}");

            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + OTHER_ID + "/children", otherParent, SCOPE)).isFalse();
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + PAGE_ID + "/children", nested, SCOPE)).isFalse();
        }

        @Test
        void 범위_밖_블록_뒤에_추가하면_막는다() {
            // given
            JsonNode body = json("{\"children\": [{\"type\": \"paragraph\", \"paragraph\": {\"rich_text\": []}}], \"after\": \"" + OTHER_ID + "\"}");

            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/blocks/" + PAGE_ID + "/children", body, SCOPE)).isFalse();
        }

        @Test
        void 페이지_속성_수정을_막는다() {
            // when & then
            assertThat(NotionWriteGuard.isAllowed(HttpMethod.PATCH, "/v1/pages/" + PAGE_ID, json("{\"properties\": {}}"), SCOPE)).isFalse();
        }
    }

    private static JsonNode textBody(
            String type,
            String text
    ) {
        return json("{\"" + type + "\": {\"rich_text\": [{\"type\": \"text\", \"text\": {\"content\": \"" + text + "\"}}]}}");
    }

    private static JsonNode rowBody(String... cells) {
        StringBuilder array = new StringBuilder();
        for (String cell : cells) {
            if (!array.isEmpty()) {
                array.append(", ");
            }
            array.append("[{\"type\": \"text\", \"text\": {\"content\": \"").append(cell).append("\"}}]");
        }
        return json("{\"table_row\": {\"cells\": [" + array + "]}}");
    }

    private static JsonNode json(String value) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
