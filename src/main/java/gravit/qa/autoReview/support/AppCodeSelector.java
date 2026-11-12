package gravit.qa.autoReview.support;

import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.autoReview.dto.internal.ApiEndpointDto;
import gravit.qa.autoReview.dto.internal.SourceFileDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AppCodeSelector {

    public static final String AOS_SOURCE_ROOT = "app/src/main/java/";
    public static final List<String> NATIVE_SHELL_ROOTS = List.of("apps/native/app/", "apps/native/bridge/");

    private static final String KOTLIN = ".kt";
    private static final Pattern RETROFIT_CALL = Pattern.compile("@(GET|POST|PUT|PATCH|DELETE)\\(\\s*\"([^\"]*)\"\\s*\\)");
    private static final Pattern FUNCTION = Pattern.compile("fun\\s+(\\w+)");
    private static final Pattern DECLARATION = Pattern.compile("(?:class|object|interface)\\s+(\\w+)");
    private static final Pattern NATIVE_SOURCE = Pattern.compile("\\.(tsx?|jsx?)$");
    private static final Pattern EXCLUDED_FILE = Pattern.compile("\\.(test|spec)\\.");
    private static final String LINE_BREAK = "\n";
    private static final int FUNCTION_LOOKAHEAD = 400;
    private static final int EXCERPT_LINES = 10;

    public static List<SourceFileDto> select(
            Collection<String> aosPaths,
            SourceLoader aosLoader,
            Collection<String> webPaths,
            SourceLoader webLoader,
            List<ApiEndpointDto> endpoints,
            boolean includeShell
    ) {
        Map<String, String> pathToSource = new LinkedHashMap<>();
        aosPaths.stream()
                .filter(path -> path.startsWith(AOS_SOURCE_ROOT) && path.endsWith(KOTLIN))
                .sorted()
                .forEach(path -> aosLoader.load(path).ifPresent(source -> pathToSource.put(path, source)));

        List<SourceFileDto> files = new ArrayList<>();
        Set<String> apiFiles = new LinkedHashSet<>();
        Set<String> functions = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : pathToSource.entrySet()) {
            List<SourceFileDto> excerpts = apiExcerpts(entry.getKey(), entry.getValue(), endpoints, functions);
            if (!excerpts.isEmpty()) {
                apiFiles.add(entry.getKey());
                files.addAll(excerpts);
            }
        }

        Set<String> selected = new LinkedHashSet<>(apiFiles);
        List<String> callers = pathToSource.entrySet().stream()
                .filter(entry -> !selected.contains(entry.getKey()))
                .filter(entry -> functions.stream().anyMatch(function -> callsFunction(entry.getValue(), function)))
                .map(Map.Entry::getKey)
                .toList();
        selected.addAll(callers);

        Set<String> declared = new LinkedHashSet<>();
        callers.forEach(path -> declared.addAll(declarations(pathToSource.get(path))));
        List<String> users = pathToSource.entrySet().stream()
                .filter(entry -> !selected.contains(entry.getKey()))
                .filter(entry -> declared.stream().anyMatch(name -> Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(entry.getValue()).find()))
                .map(Map.Entry::getKey)
                .toList();

        callers.forEach(path -> files.add(whole(GithubRepo.GRAVIT_AOS, path, pathToSource.get(path))));
        users.forEach(path -> files.add(whole(GithubRepo.GRAVIT_AOS, path, pathToSource.get(path))));

        if (!includeShell) {
            return files;
        }
        webPaths.stream()
                .filter(path -> NATIVE_SHELL_ROOTS.stream().anyMatch(path::startsWith))
                .filter(path -> NATIVE_SOURCE.matcher(path).find() && !EXCLUDED_FILE.matcher(path).find())
                .sorted()
                .forEach(path -> webLoader.load(path).ifPresent(source -> files.add(whole(GithubRepo.GRAVIT_WEB, path, source))));
        return files;
    }

    private static List<SourceFileDto> apiExcerpts(
            String path,
            String source,
            List<ApiEndpointDto> endpoints,
            Set<String> functions
    ) {
        List<String> lines = Arrays.asList(source.split(LINE_BREAK, -1));
        List<SourceFileDto> excerpts = new ArrayList<>();
        Matcher call = RETROFIT_CALL.matcher(source);
        while (call.find()) {
            ApiEndpointDto endpoint = new ApiEndpointDto(call.group(1), ApiPaths.normalize(call.group(2)));
            if (!endpoints.contains(endpoint)) {
                continue;
            }
            Matcher function = FUNCTION.matcher(source.substring(call.end(), Math.min(source.length(), call.end() + FUNCTION_LOOKAHEAD)));
            if (function.find()) {
                functions.add(function.group(1));
            }
            int line = lineOf(source, call.start());
            int end = Math.min(lines.size(), line + EXCERPT_LINES);
            excerpts.add(SourceFileDto.of(GithubRepo.GRAVIT_AOS, path, lines.subList(line - 1, end), line));
        }
        return excerpts;
    }

    private static boolean callsFunction(
            String source,
            String function
    ) {
        return Pattern.compile("(\\.|::)" + Pattern.quote(function) + "\\b").matcher(source).find();
    }

    private static List<String> declarations(String source) {
        List<String> names = new ArrayList<>();
        Matcher declaration = DECLARATION.matcher(source);
        while (declaration.find()) {
            names.add(declaration.group(1));
        }
        return names;
    }

    private static SourceFileDto whole(
            GithubRepo repo,
            String path,
            String source
    ) {
        return SourceFileDto.of(repo, path, Arrays.asList(source.split(LINE_BREAK, -1)), 1);
    }

    private static int lineOf(
            String source,
            int index
    ) {
        int line = 1;
        for (int position = 0; position < index; position++) {
            if (source.charAt(position) == '\n') {
                line++;
            }
        }
        return line;
    }
}
