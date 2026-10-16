package gravit.qa.adapter.llm.support;

import com.networknt.schema.JsonSchema;

public record LlmOutputSchema<T>(
        Class<T> type,
        String format,
        JsonSchema jsonSchema
) {
}
