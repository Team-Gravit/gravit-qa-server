package gravit.qa.autoReview.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WebCodeSelector {

    public static final String WEB_SOURCE_ROOT = "apps/web/src/";
    public static final String ROUTES_ROOT = WEB_SOURCE_ROOT + "app/routes/";

    private static final String ALIAS_PREFIX = "@/";
    private static final String SLASH = "/";
    private static final String PARAM = "$";
    private static final List<String> SOURCE_EXTENSIONS = List.of(".tsx", ".ts");
    private static final List<String> RESOLVE_SUFFIXES = List.of("", ".ts", ".tsx", "/index.ts", "/index.tsx");
    private static final Set<String> DROPPED_SEGMENTS = Set.of("index", "route", "__root");
    private static final List<String> EXCLUDED_PREFIXES = List.of(WEB_SOURCE_ROOT + "shared/ui/");
    private static final Pattern EXCLUDED_FILE = Pattern.compile("\\.(test|spec|stories)\\.tsx?$");
    private static final Pattern IMPORT = Pattern.compile("(?:import|export)\\s[^;'\"]*?from\\s*['\"]([^'\"]+)['\"]|import\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)|import\\s+['\"]([^'\"]+)['\"]");
    private static final Pattern ROUTE_SPLIT = Pattern.compile("[,\\s]+");
    private static final Pattern SPEC_PARAM = Pattern.compile("(\\$\\w+|:\\w+|\\{[^}]*})");
    private static final int MAX_DEPTH = 3;

    public static List<String> select(
            Collection<String> paths,
            SourceLoader loader,
            String routeSpec
    ) {
        List<String> routes = routesOf(routeSpec);
        if (routes.isEmpty()) {
            return List.of();
        }
        Set<String> sources = paths.stream()
                .filter(path -> path.startsWith(WEB_SOURCE_ROOT) && isSource(path))
                .collect(Collectors.toSet());

        Map<String, Integer> pathToDepth = new LinkedHashMap<>();
        sources.stream()
                .filter(path -> path.startsWith(ROUTES_ROOT))
                .filter(path -> routeOf(path).map(routes::contains).orElse(false))
                .sorted()
                .forEach(path -> pathToDepth.put(path, 0));

        List<String> frontier = new ArrayList<>(pathToDepth.keySet());
        for (int depth = 1; depth <= MAX_DEPTH && !frontier.isEmpty(); depth++) {
            List<String> next = new ArrayList<>();
            for (String path : frontier) {
                Optional<String> source = loader.load(path);
                if (source.isEmpty()) {
                    continue;
                }
                for (String target : imports(path, source.get(), sources)) {
                    if (!pathToDepth.containsKey(target) && EXCLUDED_PREFIXES.stream().noneMatch(target::startsWith)) {
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

    static Optional<String> routeOf(String routeFile) {
        String relative = routeFile.substring(ROUTES_ROOT.length());
        String extension = SOURCE_EXTENSIONS.stream().filter(relative::endsWith).findFirst().orElse(null);
        if (extension == null) {
            return Optional.empty();
        }
        relative = relative.substring(0, relative.length() - extension.length());

        List<String> segments = new ArrayList<>();
        for (String directory : relative.split(SLASH)) {
            for (String token : directory.split("\\.")) {
                if (token.startsWith("-")) {
                    return Optional.empty();
                }
                if (token.isEmpty() || DROPPED_SEGMENTS.contains(token) || (token.startsWith("_") && !token.startsWith(PARAM))) {
                    continue;
                }
                String value = token.endsWith("_") ? token.substring(0, token.length() - 1) : token;
                segments.add(value.startsWith(PARAM) ? PARAM : value);
            }
        }
        return Optional.of(SLASH + String.join(SLASH, segments));
    }

    static List<String> routesOf(String routeSpec) {
        if (routeSpec == null || routeSpec.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ROUTE_SPLIT.split(routeSpec.trim()))
                .filter(route -> route.startsWith(SLASH))
                .map(WebCodeSelector::normalizeRoute)
                .distinct()
                .toList();
    }

    private static String normalizeRoute(String route) {
        String value = SPEC_PARAM.matcher(route).replaceAll(Matcher.quoteReplacement(PARAM));
        if (value.length() > 1 && value.endsWith(SLASH)) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static List<String> imports(
            String path,
            String source,
            Set<String> sources
    ) {
        List<String> targets = new ArrayList<>();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            String specifier = matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            resolve(path, specifier, sources).ifPresent(targets::add);
        }
        return targets;
    }

    private static Optional<String> resolve(
            String from,
            String specifier,
            Set<String> sources
    ) {
        String base;
        if (specifier.startsWith(ALIAS_PREFIX)) {
            base = WEB_SOURCE_ROOT + specifier.substring(ALIAS_PREFIX.length());
        } else if (specifier.startsWith(".")) {
            base = normalizePath(from.substring(0, from.lastIndexOf('/') + 1) + specifier);
        } else {
            return Optional.empty();
        }
        return RESOLVE_SUFFIXES.stream()
                .map(suffix -> base + suffix)
                .filter(sources::contains)
                .findFirst();
    }

    private static String normalizePath(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split(SLASH)) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!parts.isEmpty()) {
                    parts.removeLast();
                }
                continue;
            }
            parts.add(part);
        }
        return String.join(SLASH, parts);
    }

    private static boolean isSource(String path) {
        return SOURCE_EXTENSIONS.stream().anyMatch(path::endsWith) && !EXCLUDED_FILE.matcher(path).find();
    }
}
