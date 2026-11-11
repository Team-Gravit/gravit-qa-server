package gravit.qa.autoReview.support;

import gravit.qa.autoReview.dto.internal.ApiEndpointDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServerCodeSelector {

    private static final String SOURCE_ROOT = "src/main/java/";
    private static final String JAVA = ".java";
    private static final String CONTROLLER_SUFFIX = "Controller.java";
    private static final String DOCS_SUFFIX = "Docs.java";
    private static final Pattern CLASS_DECLARATION = Pattern.compile("\\b(class|interface|record)\\s+\\w+");
    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\s*\\(\\s*(?:(?:value|path)\\s*=\\s*)?\\{?\\s*\"([^\"]*)\"");
    private static final Pattern METHOD_MAPPING = Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping\\b(?:\\s*\\(([^)]*)\\))?");
    private static final Pattern REQUEST_MAPPING = Pattern.compile("@RequestMapping\\s*\\(([^)]*)\\)");
    private static final Pattern REQUEST_METHOD = Pattern.compile("RequestMethod\\.(GET|POST|PUT|PATCH|DELETE)");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern INJECTED_FIELD = Pattern.compile("private\\s+final\\s+([A-Z]\\w*)(?:<[^>]*>)?\\s+\\w+\\s*;");
    private static final Pattern DTO_REFERENCE = Pattern.compile("\\b([A-Z]\\w*(?:Request|Response|Dto))\\b");
    private static final List<String> LOGIC_SUFFIXES = List.of(
            "Service", "Facade", "Validator", "Policy", "Manager", "Calculator", "Reader", "Writer",
            "Provider", "Factory", "Processor", "Handler", "Executor", "Generator", "Converter", "Mapper", "Repository"
    );
    private static final int MAX_DEPTH = 2;

    public static List<String> select(
            Collection<String> paths,
            SourceLoader loader,
            List<ApiEndpointDto> endpoints
    ) {
        if (endpoints.isEmpty()) {
            return List.of();
        }
        Map<String, String> nameToPath = new TreeMap<>();
        paths.stream()
                .filter(path -> path.startsWith(SOURCE_ROOT) && path.endsWith(JAVA))
                .sorted()
                .forEach(path -> nameToPath.putIfAbsent(simpleName(path), path));

        Map<String, Integer> pathToDepth = new LinkedHashMap<>();
        for (String path : nameToPath.values()) {
            if (path.endsWith(CONTROLLER_SUFFIX) && !path.endsWith(DOCS_SUFFIX)) {
                loader.load(path)
                        .filter(source -> mapsAny(source, endpoints))
                        .ifPresent(source -> pathToDepth.put(path, 0));
            }
        }

        List<String> frontier = new ArrayList<>(pathToDepth.keySet());
        for (int depth = 1; depth <= MAX_DEPTH && !frontier.isEmpty(); depth++) {
            List<String> next = new ArrayList<>();
            for (String path : frontier) {
                Optional<String> source = loader.load(path);
                if (source.isEmpty()) {
                    continue;
                }
                for (String referenced : references(source.get(), depth == 1)) {
                    String target = nameToPath.get(referenced);
                    if (target != null && !target.endsWith(DOCS_SUFFIX) && !pathToDepth.containsKey(target)) {
                        pathToDepth.put(target, depth);
                        next.add(target);
                    }
                }
            }
            frontier = next;
        }
        return pathToDepth.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    static boolean mapsAny(
            String source,
            List<ApiEndpointDto> endpoints
    ) {
        Set<ApiEndpointDto> mapped = new LinkedHashSet<>(mappings(source));
        return endpoints.stream().anyMatch(mapped::contains);
    }

    static List<ApiEndpointDto> mappings(String source) {
        Matcher declaration = CLASS_DECLARATION.matcher(source);
        int classStart = declaration.find() ? declaration.start() : source.length();
        Matcher classMapping = CLASS_MAPPING.matcher(source.substring(0, classStart));
        String prefix = classMapping.find() ? classMapping.group(1) : "";
        String body = source.substring(classStart);

        List<ApiEndpointDto> endpoints = new ArrayList<>();
        Matcher method = METHOD_MAPPING.matcher(body);
        while (method.find()) {
            String httpMethod = method.group(1).toUpperCase();
            endpoints.add(new ApiEndpointDto(httpMethod, ApiPaths.join(prefix, firstLiteral(method.group(2)))));
        }
        Matcher request = REQUEST_MAPPING.matcher(body);
        while (request.find()) {
            Matcher requestMethod = REQUEST_METHOD.matcher(request.group(1));
            if (requestMethod.find()) {
                endpoints.add(new ApiEndpointDto(requestMethod.group(1), ApiPaths.join(prefix, firstLiteral(request.group(1)))));
            }
        }
        return endpoints;
    }

    private static List<String> references(
            String source,
            boolean includeDtos
    ) {
        Set<String> names = new LinkedHashSet<>();
        Matcher field = INJECTED_FIELD.matcher(source);
        while (field.find()) {
            String type = field.group(1);
            if (LOGIC_SUFFIXES.stream().anyMatch(type::endsWith)) {
                names.add(type);
            }
        }
        if (includeDtos) {
            Matcher dto = DTO_REFERENCE.matcher(source);
            while (dto.find()) {
                names.add(dto.group(1));
            }
        }
        return List.copyOf(names);
    }

    private static String firstLiteral(String arguments) {
        if (arguments == null) {
            return "";
        }
        Matcher literal = STRING_LITERAL.matcher(arguments);
        return literal.find() ? literal.group(1) : "";
    }

    private static String simpleName(String path) {
        String file = path.substring(path.lastIndexOf('/') + 1);
        return file.substring(0, file.length() - JAVA.length());
    }
}
