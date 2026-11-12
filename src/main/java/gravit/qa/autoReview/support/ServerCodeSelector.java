package gravit.qa.autoReview.support;

import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.autoReview.dto.internal.ApiEndpointDto;
import gravit.qa.autoReview.dto.internal.SourceFileDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServerCodeSelector {

    private static final String SOURCE_ROOT = "src/main/java/";
    private static final String JAVA = ".java";
    private static final String CONTROLLER_SUFFIX = "Controller.java";
    private static final String DOCS_SUFFIX = "Docs.java";
    private static final String PACKAGE_SEPARATOR = ".";
    private static final String LINE_BREAK = "\n";
    private static final Pattern CLASS_DECLARATION = Pattern.compile("\\b(class|interface|record)\\s+\\w+");
    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\s*\\(\\s*(?:(?:value|path)\\s*=\\s*)?\\{?\\s*\"([^\"]*)\"");
    private static final Pattern METHOD_MAPPING = Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping\\b(?:\\s*\\(([^)]*)\\))?");
    private static final Pattern REQUEST_MAPPING = Pattern.compile("@RequestMapping\\s*\\(([^)]*)\\)");
    private static final Pattern REQUEST_METHOD = Pattern.compile("RequestMethod\\.(GET|POST|PUT|PATCH|DELETE)");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern QUOTED_TEXT = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern INJECTED_FIELD = Pattern.compile("private\\s+final\\s+([A-Z]\\w*)(?:<[^>]*>)?\\s+\\w+\\s*;");
    private static final Pattern DTO_REFERENCE = Pattern.compile("\\b([A-Z]\\w*(?:Request|Response|Dto))\\b");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE_IMPORT = Pattern.compile("^\\s*import\\s+(?!static\\s)([\\w.]+?)(\\.\\*)?\\s*;", Pattern.MULTILINE);
    private static final Pattern STATIC_IMPORT = Pattern.compile("^\\s*import\\s+static\\s+([\\w.]+)\\.(\\w+)\\s*;", Pattern.MULTILINE);
    private static final String ENUM_CONSTANT_DECLARATION = "^\\s*(%s)\\b\\s*(?:[(,;{]|$)";
    private static final String FIELD_CONSTANT_DECLARATION = "\\bstatic\\b[^=]*\\b(%s)\\s*=";
    private static final String ALTERNATION = "|";
    private static final List<String> LOGIC_SUFFIXES = List.of(
            "Service", "Facade", "Validator", "Policy", "Manager", "Calculator", "Reader", "Writer",
            "Provider", "Factory", "Processor", "Handler", "Executor", "Generator", "Converter", "Mapper", "Repository"
    );
    private static final int MAX_DEPTH = 2;
    private static final int MAX_CONSTANT_LINES = 5;

    public static List<SourceFileDto> select(
            Collection<String> paths,
            SourceLoader loader,
            List<ApiEndpointDto> endpoints
    ) {
        if (endpoints.isEmpty()) {
            return List.of();
        }
        Map<String, Optional<String>> pathToSource = new HashMap<>();
        SourceLoader cached = path -> pathToSource.computeIfAbsent(path, loader::load);

        Map<String, String> qualifiedNameToPath = new TreeMap<>();
        Map<String, List<String>> nameToPaths = new TreeMap<>();
        paths.stream()
                .filter(path -> path.startsWith(SOURCE_ROOT) && path.endsWith(JAVA))
                .sorted()
                .forEach(path -> {
                    qualifiedNameToPath.put(qualifiedName(path), path);
                    nameToPaths.computeIfAbsent(simpleName(path), name -> new ArrayList<>()).add(path);
                });

        Map<String, Integer> pathToDepth = new LinkedHashMap<>();
        for (String path : qualifiedNameToPath.values()) {
            if (path.endsWith(CONTROLLER_SUFFIX) && !path.endsWith(DOCS_SUFFIX)) {
                cached.load(path)
                        .filter(source -> mapsAny(source, endpoints))
                        .ifPresent(source -> pathToDepth.put(path, 0));
            }
        }

        List<String> frontier = new ArrayList<>(pathToDepth.keySet());
        for (int depth = 1; depth <= MAX_DEPTH && !frontier.isEmpty(); depth++) {
            List<String> next = new ArrayList<>();
            for (String path : frontier) {
                Optional<String> source = cached.load(path);
                if (source.isEmpty()) {
                    continue;
                }
                for (String referenced : references(source.get(), depth == 1)) {
                    Optional<String> target = resolve(referenced, source.get(), qualifiedNameToPath, nameToPaths);
                    if (target.isPresent() && !target.get().endsWith(DOCS_SUFFIX) && !pathToDepth.containsKey(target.get())) {
                        pathToDepth.put(target.get(), depth);
                        next.add(target.get());
                    }
                }
            }
            frontier = next;
        }

        Map<String, String> selectedToSource = new LinkedHashMap<>();
        pathToDepth.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .forEach(path -> cached.load(path).ifPresent(source -> selectedToSource.put(path, source)));

        List<SourceFileDto> files = new ArrayList<>();
        selectedToSource.forEach((path, source) -> files.add(whole(path, source)));
        files.addAll(constantExcerpts(selectedToSource, qualifiedNameToPath, cached));
        return files;
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

    static Optional<String> resolve(
            String name,
            String source,
            Map<String, String> qualifiedNameToPath,
            Map<String, List<String>> nameToPaths
    ) {
        List<String> wildcardPackages = new ArrayList<>();
        Matcher imported = TYPE_IMPORT.matcher(source);
        while (imported.find()) {
            String qualifiedName = imported.group(1);
            if (imported.group(2) != null) {
                wildcardPackages.add(qualifiedName);
            } else if (qualifiedName.endsWith(PACKAGE_SEPARATOR + name)) {
                return Optional.ofNullable(qualifiedNameToPath.get(qualifiedName));
            }
        }

        Matcher declaredPackage = PACKAGE_DECLARATION.matcher(source);
        if (declaredPackage.find()) {
            String samePackage = qualifiedNameToPath.get(declaredPackage.group(1) + PACKAGE_SEPARATOR + name);
            if (samePackage != null) {
                return Optional.of(samePackage);
            }
        }

        for (String wildcardPackage : wildcardPackages) {
            String path = qualifiedNameToPath.get(wildcardPackage + PACKAGE_SEPARATOR + name);
            if (path != null) {
                return Optional.of(path);
            }
        }

        List<String> candidates = nameToPaths.getOrDefault(name, List.of());
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private static List<SourceFileDto> constantExcerpts(
            Map<String, String> selectedToSource,
            Map<String, String> qualifiedNameToPath,
            SourceLoader loader
    ) {
        Map<String, Set<String>> pathToConstants = new TreeMap<>();
        for (String source : selectedToSource.values()) {
            Matcher imported = STATIC_IMPORT.matcher(source);
            while (imported.find()) {
                String target = qualifiedNameToPath.get(imported.group(1));
                if (target != null && !selectedToSource.containsKey(target)) {
                    pathToConstants.computeIfAbsent(target, path -> new LinkedHashSet<>()).add(imported.group(2));
                }
            }
        }

        List<SourceFileDto> excerpts = new ArrayList<>();
        pathToConstants.forEach((path, constants) -> loader.load(path)
                .ifPresent(source -> excerpts.addAll(constantDeclarations(path, source, constants))));
        return excerpts;
    }

    private static List<SourceFileDto> constantDeclarations(
            String path,
            String source,
            Set<String> constants
    ) {
        String names = constants.stream().map(Pattern::quote).collect(Collectors.joining(ALTERNATION));
        Pattern enumConstant = Pattern.compile(ENUM_CONSTANT_DECLARATION.formatted(names));
        Pattern fieldConstant = Pattern.compile(FIELD_CONSTANT_DECLARATION.formatted(names));
        List<String> lines = Arrays.asList(source.split(LINE_BREAK, -1));

        List<SourceFileDto> excerpts = new ArrayList<>();
        Set<String> found = new LinkedHashSet<>();
        int index = 0;
        while (index < lines.size()) {
            Optional<String> declared = declaredConstant(lines.get(index), enumConstant, fieldConstant);
            if (declared.isPresent() && found.add(declared.get())) {
                int end = declarationEnd(lines, index);
                excerpts.add(SourceFileDto.of(GithubRepo.GRAVIT_SERVER, path, lines.subList(index, end + 1), index + 1));
                index = end;
            }
            index++;
        }
        return excerpts;
    }

    private static Optional<String> declaredConstant(
            String line,
            Pattern enumConstant,
            Pattern fieldConstant
    ) {
        Matcher enumMatcher = enumConstant.matcher(line);
        if (enumMatcher.find()) {
            return Optional.of(enumMatcher.group(1));
        }
        Matcher fieldMatcher = fieldConstant.matcher(line);
        return fieldMatcher.find() ? Optional.of(fieldMatcher.group(1)) : Optional.empty();
    }

    private static int declarationEnd(
            List<String> lines,
            int start
    ) {
        long depth = 0;
        int last = Math.min(lines.size(), start + MAX_CONSTANT_LINES) - 1;
        for (int index = start; index <= last; index++) {
            String code = QUOTED_TEXT.matcher(lines.get(index)).replaceAll("");
            depth += code.chars().filter(ch -> ch == '(').count() - code.chars().filter(ch -> ch == ')').count();
            if (depth <= 0) {
                return index;
            }
        }
        return last;
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

    private static SourceFileDto whole(
            String path,
            String source
    ) {
        return SourceFileDto.of(GithubRepo.GRAVIT_SERVER, path, Arrays.asList(source.split(LINE_BREAK, -1)), 1);
    }

    private static String firstLiteral(String arguments) {
        if (arguments == null) {
            return "";
        }
        Matcher literal = STRING_LITERAL.matcher(arguments);
        return literal.find() ? literal.group(1) : "";
    }

    private static String qualifiedName(String path) {
        return path.substring(SOURCE_ROOT.length(), path.length() - JAVA.length()).replace('/', '.');
    }

    private static String simpleName(String path) {
        String file = path.substring(path.lastIndexOf('/') + 1);
        return file.substring(0, file.length() - JAVA.length());
    }
}
