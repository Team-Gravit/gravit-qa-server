package gravit.qa.adapter.openapi.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.openapi.dto.internal.OpenApiExcerptDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpenApiExtractor {

    private static final List<String> HTTP_METHODS = List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final String COMPONENT_REF_PREFIX = "#/components/";
    private static final String REF_FIELD = "$ref";
    private static final String OPENAPI_FIELD = "openapi";
    private static final String PATHS_FIELD = "paths";
    private static final String COMPONENTS_FIELD = "components";
    private static final String PARAMETERS_FIELD = "parameters";
    private static final String OPERATION_ID_FIELD = "operationId";
    private static final String POINTER_SEPARATOR = "/";
    private static final int COMPONENT_TYPE_INDEX = 2;
    private static final int COMPONENT_NAME_INDEX = 3;

    public static OpenApiExcerptDto extract(
            JsonNode document,
            Collection<String> operationIds
    ) {
        Set<String> wanted = new LinkedHashSet<>(operationIds);
        Set<String> found = new LinkedHashSet<>();
        Deque<String> pendingRefs = new ArrayDeque<>();

        ObjectNode excerpt = JsonNodeFactory.instance.objectNode();
        if (document.has(OPENAPI_FIELD)) {
            excerpt.set(OPENAPI_FIELD, document.get(OPENAPI_FIELD).deepCopy());
        }
        ObjectNode paths = excerpt.putObject(PATHS_FIELD);

        for (Map.Entry<String, JsonNode> pathEntry : document.path(PATHS_FIELD).properties()) {
            JsonNode pathItem = pathEntry.getValue();
            for (String method : HTTP_METHODS) {
                JsonNode operation = pathItem.get(method);
                if (operation == null || !wanted.contains(operation.path(OPERATION_ID_FIELD).asText())) {
                    continue;
                }

                ObjectNode targetItem = paths.has(pathEntry.getKey())
                        ? (ObjectNode) paths.get(pathEntry.getKey())
                        : paths.putObject(pathEntry.getKey());
                if (pathItem.has(PARAMETERS_FIELD) && !targetItem.has(PARAMETERS_FIELD)) {
                    targetItem.set(PARAMETERS_FIELD, pathItem.get(PARAMETERS_FIELD).deepCopy());
                    collectRefs(pathItem.get(PARAMETERS_FIELD), pendingRefs);
                }
                targetItem.set(method, operation.deepCopy());
                collectRefs(operation, pendingRefs);
                found.add(operation.path(OPERATION_ID_FIELD).asText());
            }
        }

        ObjectNode components = resolveComponents(document, pendingRefs);
        if (!components.isEmpty()) {
            excerpt.set(COMPONENTS_FIELD, components);
        }

        List<String> missing = wanted.stream().filter(operationId -> !found.contains(operationId)).toList();
        return new OpenApiExcerptDto(excerpt, List.copyOf(found), missing);
    }

    private static ObjectNode resolveComponents(
            JsonNode document,
            Deque<String> pendingRefs
    ) {
        ObjectNode components = JsonNodeFactory.instance.objectNode();
        Set<String> visited = new HashSet<>();
        while (!pendingRefs.isEmpty()) {
            String ref = pendingRefs.poll();
            if (!visited.add(ref)) {
                continue;
            }

            JsonNode target = document.at(ref.substring(1));
            String[] segments = ref.substring(1).split(POINTER_SEPARATOR);
            if (target.isMissingNode() || segments.length <= COMPONENT_NAME_INDEX) {
                continue;
            }
            components.withObjectProperty(unescape(segments[COMPONENT_TYPE_INDEX])).set(unescape(segments[COMPONENT_NAME_INDEX]), target.deepCopy());
            collectRefs(target, pendingRefs);
        }
        return components;
    }

    private static void collectRefs(
            JsonNode node,
            Deque<String> pendingRefs
    ) {
        if (node.isObject()) {
            JsonNode ref = node.get(REF_FIELD);
            if (ref != null && ref.isTextual() && ref.asText().startsWith(COMPONENT_REF_PREFIX)) {
                pendingRefs.add(ref.asText());
            }
            node.properties().forEach(entry -> collectRefs(entry.getValue(), pendingRefs));
        } else if (node.isArray()) {
            node.forEach(child -> collectRefs(child, pendingRefs));
        }
    }

    private static String unescape(String segment) {
        return segment.replace("~1", "/").replace("~0", "~");
    }
}
