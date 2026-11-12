package gravit.qa.adapter.llm.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import gravit.qa.adapter.llm.dto.internal.LlmValidationResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
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
    private static final String TYPE_FIELD = "type";
    private static final String ENUM_FIELD = "enum";
    private static final String PROPERTIES_FIELD = "properties";
    private static final String ITEMS_FIELD = "items";
    private static final String REF_FIELD = "$ref";
    private static final String LOCAL_REF_PREFIX = "#";
    private static final String NULL_TYPE = "null";

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
        String generatedSchema = converter.getJsonSchema();
        String jsonSchema = allowNullComponents(outputType, generatedSchema);
        return new LlmOutputSchema<>(
                outputType,
                converter.getFormat().replace(generatedSchema, jsonSchema),
                SCHEMA_FACTORY.getSchema(jsonSchema, SCHEMA_CONFIG)
        );
    }

    private String allowNullComponents(
            Class<?> outputType,
            String jsonSchema
    ) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(jsonSchema);
            markNullable(outputType, root, root);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(outputType.getSimpleName() + " 출력 스키마를 해석할 수 없습니다.", e);
        }
    }

    private void markNullable(
            Class<?> type,
            JsonNode schema,
            ObjectNode root
    ) {
        if (!type.isRecord()) {
            return;
        }
        JsonNode properties = resolve(schema, root).path(PROPERTIES_FIELD);
        for (RecordComponent component : type.getRecordComponents()) {
            JsonNode property = properties.get(component.getName());
            if (property == null) {
                continue;
            }
            if (component.getAccessor().isAnnotationPresent(Nullable.class)) {
                allowNull(component, resolve(property, root));
            }

            Class<?> componentType = component.getType();
            if (componentType.isRecord()) {
                markNullable(componentType, property, root);
            } else if (Collection.class.isAssignableFrom(componentType)) {
                Class<?> elementType = elementTypeOf(component.getGenericType());
                if (elementType != null) {
                    markNullable(elementType, property.path(ITEMS_FIELD), root);
                }
            }
        }
    }

    private JsonNode resolve(
            JsonNode schema,
            ObjectNode root
    ) {
        JsonNode ref = schema.get(REF_FIELD);
        if (ref != null && ref.isTextual() && ref.asText().startsWith(LOCAL_REF_PREFIX)) {
            return root.at(ref.asText().substring(LOCAL_REF_PREFIX.length()));
        }
        return schema;
    }

    private void allowNull(
            RecordComponent component,
            JsonNode property
    ) {
        JsonNode type = property.get(TYPE_FIELD);
        if (type != null && type.isArray()) {
            return;
        }
        if (!(property instanceof ObjectNode node) || type == null || !type.isTextual()) {
            throw new IllegalStateException(component.getDeclaringRecord().getSimpleName() + "." + component.getName()
                    + "의 스키마에는 null을 허용할 type이 없어 @Nullable을 쓸 수 없습니다.");
        }

        ArrayNode types = objectMapper.createArrayNode().add(type.asText()).add(NULL_TYPE);
        node.set(TYPE_FIELD, types);
        if (node.get(ENUM_FIELD) instanceof ArrayNode values) {
            values.addNull();
        }
    }

    private Class<?> elementTypeOf(Type genericType) {
        if (genericType instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments().length == 1
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> elementType) {
            return elementType;
        }
        return null;
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
