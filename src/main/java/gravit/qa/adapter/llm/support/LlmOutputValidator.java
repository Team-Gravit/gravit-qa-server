package gravit.qa.adapter.llm.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import gravit.qa.adapter.llm.dto.internal.LlmValidationResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class LlmOutputValidator {

    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig SCHEMA_CONFIG = SchemaValidatorsConfig.builder().locale(Locale.KOREAN).build();
    private static final String CODE_FENCE = "```";
    private static final String LINE_BREAK = "\n";

    private final Map<Class<?>, LlmOutputSchema<?>> typeToSchema = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public <T> LlmOutputSchema<T> schemaOf(Class<T> outputType) {
        return (LlmOutputSchema<T>) typeToSchema.computeIfAbsent(outputType, this::createSchema);
    }

    public <T> LlmValidationResultDto<T> validate(
            String output,
            LlmOutputSchema<T> schema,
            Function<T, List<String>> semanticValidator
    ) {
        if (output == null || output.isBlank()) {
            return LlmValidationResultDto.invalid(List.of("응답이 비어 있습니다."));
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(stripCodeFence(output));
        } catch (JsonProcessingException e) {
            return LlmValidationResultDto.invalid(List.of("JSON으로 해석할 수 없습니다: " + e.getOriginalMessage()));
        }

        Set<ValidationMessage> violations = schema.jsonSchema().validate(node);
        if (!violations.isEmpty()) {
            return LlmValidationResultDto.invalid(violations.stream().map(ValidationMessage::getMessage).sorted().toList());
        }

        T value;
        try {
            value = objectMapper.treeToValue(node, schema.type());
        } catch (JsonProcessingException e) {
            return LlmValidationResultDto.invalid(List.of("출력 형식으로 변환할 수 없습니다: " + e.getOriginalMessage()));
        }

        List<String> semanticErrors = semanticValidator.apply(value);
        if (semanticErrors != null && !semanticErrors.isEmpty()) {
            return LlmValidationResultDto.invalid(semanticErrors);
        }
        return LlmValidationResultDto.valid(value);
    }

    private LlmOutputSchema<?> createSchema(Class<?> outputType) {
        BeanOutputConverter<?> converter = new BeanOutputConverter<>(outputType, objectMapper);
        return new LlmOutputSchema<>(
                outputType,
                converter.getFormat(),
                SCHEMA_FACTORY.getSchema(converter.getJsonSchema(), SCHEMA_CONFIG)
        );
    }

    private String stripCodeFence(String output) {
        String trimmed = output.trim();
        if (!trimmed.startsWith(CODE_FENCE)) {
            return trimmed;
        }

        List<String> lines = new ArrayList<>(List.of(trimmed.split(LINE_BREAK)));
        lines.removeFirst();
        if (!lines.isEmpty() && lines.getLast().trim().equals(CODE_FENCE)) {
            lines.removeLast();
        }
        return String.join(LINE_BREAK, lines);
    }
}
