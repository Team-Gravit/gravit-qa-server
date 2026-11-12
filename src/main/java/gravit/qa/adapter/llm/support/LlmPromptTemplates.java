package gravit.qa.adapter.llm.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmPromptTemplates {

    private static final String TEMPLATE_LOCATION = "prompts/%s.txt";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z]+)}}");

    private final Map<String, String> nameToTemplate = new ConcurrentHashMap<>();

    public String render(
            String name,
            Map<String, String> values
    ) {
        String template = nameToTemplate.computeIfAbsent(name, this::load);

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                throw new IllegalStateException(name + " 프롬프트의 {{" + matcher.group(1) + "}} 값이 없습니다.");
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String load(String name) {
        String location = TEMPLATE_LOCATION.formatted(name);
        try (InputStream input = new ClassPathResource(location).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 템플릿을 읽지 못했습니다: " + location, e);
        }
    }
}
