package gravit.qa.adapter.openapi.dto.internal;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record OpenApiExcerptDto(
        JsonNode excerpt,

        List<String> foundOperationIds,

        List<String> missingOperationIds
) {
}
