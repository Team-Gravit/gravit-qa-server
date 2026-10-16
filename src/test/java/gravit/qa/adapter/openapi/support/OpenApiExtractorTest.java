package gravit.qa.adapter.openapi.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.openapi.dto.internal.OpenApiExcerptDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class OpenApiExtractorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DOCUMENT = """
            {
              "openapi": "3.1.0",
              "paths": {
                "/api/v1/units/{unitId}": {
                  "parameters": [{"$ref": "#/components/parameters/UnitId"}],
                  "get": {
                    "operationId": "getUnit",
                    "responses": {"200": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/UnitResponse"}}}}}
                  },
                  "delete": {"operationId": "deleteUnit", "responses": {"204": {"description": "삭제"}}}
                },
                "/api/v1/lessons": {
                  "post": {
                    "operationId": "submitLesson",
                    "requestBody": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/Lesson~1Request"}}}},
                    "responses": {"200": {"description": "ok"}}
                  }
                }
              },
              "components": {
                "parameters": {"UnitId": {"name": "unitId", "in": "path", "schema": {"type": "integer"}}},
                "schemas": {
                  "UnitResponse": {"type": "object", "properties": {"lessons": {"type": "array", "items": {"$ref": "#/components/schemas/LessonSummary"}}}},
                  "LessonSummary": {"type": "object", "properties": {"unit": {"$ref": "#/components/schemas/UnitResponse"}}},
                  "Lesson/Request": {"type": "object"},
                  "Unrelated": {"type": "object"}
                }
              }
            }
            """;

    @Test
    void 지정한_operationId의_경로와_참조_스키마만_추출한다() throws Exception {
        // given
        JsonNode document = OBJECT_MAPPER.readTree(DOCUMENT);

        // when
        OpenApiExcerptDto result = OpenApiExtractor.extract(document, List.of("getUnit", "submitLesson", "notExists"));

        // then
        JsonNode excerpt = result.excerpt();
        assertSoftly(softly -> {
            softly.assertThat(excerpt.path("openapi").asText()).isEqualTo("3.1.0");
            softly.assertThat(excerpt.at("/paths/~1api~1v1~1units~1{unitId}/get/operationId").asText()).isEqualTo("getUnit");
            softly.assertThat(excerpt.at("/paths/~1api~1v1~1units~1{unitId}").has("delete")).isFalse();
            softly.assertThat(excerpt.at("/paths/~1api~1v1~1units~1{unitId}/parameters").isArray()).isTrue();
            softly.assertThat(excerpt.at("/components/parameters").has("UnitId")).isTrue();
            softly.assertThat(excerpt.at("/components/schemas").has("UnitResponse")).isTrue();
            softly.assertThat(excerpt.at("/components/schemas").has("LessonSummary")).isTrue();
            softly.assertThat(excerpt.at("/components/schemas").has("Lesson/Request")).isTrue();
            softly.assertThat(excerpt.at("/components/schemas").has("Unrelated")).isFalse();
            softly.assertThat(result.foundOperationIds()).containsExactly("getUnit", "submitLesson");
            softly.assertThat(result.missingOperationIds()).containsExactly("notExists");
        });
    }
}
