package gravit.qa.adapter.openapi.dto.internal;

import com.fasterxml.jackson.databind.JsonNode;

public record OpenApiDocumentDto(
        String url,

        JsonNode document,

        String rawJson
) {
}
