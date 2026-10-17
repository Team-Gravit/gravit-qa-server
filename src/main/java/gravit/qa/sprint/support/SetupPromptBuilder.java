package gravit.qa.sprint.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.figma.dto.internal.FigmaScreenDto;
import gravit.qa.adapter.github.dto.internal.GithubChangedFileDto;
import gravit.qa.adapter.llm.dto.internal.LlmPromptDto;
import gravit.qa.adapter.llm.support.LlmPromptTemplates;
import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.adapter.openapi.dto.internal.OpenApiExcerptDto;
import gravit.qa.checklist.dto.internal.TestCatalogEntryDto;
import gravit.qa.checklist.service.port.TestCatalog;
import gravit.qa.feature.domain.Part;
import gravit.qa.persona.domain.PersonaKey;
import gravit.qa.sprint.config.SetupProps;
import gravit.qa.sprint.dto.internal.SetupDiffDto;
import gravit.qa.sprint.dto.internal.SetupFeatureSpecDto;
import gravit.qa.sprint.dto.internal.SetupPromptDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SetupPromptBuilder {

    public static final String TRUNCATION_NOTICE = "\n\n(입력 길이 상한으로 이후 내용 생략)";

    private static final String SYSTEM_TEMPLATE = "checklist-draft-system";
    private static final String USER_TEMPLATE = "checklist-draft-user";
    private static final String NONE = "(없음)";
    private static final String NO_TEST_CATALOG = "(없음) 이 화면에는 자동 테스트가 없으므로 testRef는 null로 둔다";
    private static final String NO_PREVIOUS_BASELINE = "(직전 기준점이 없어 변경 파일 목록이 없음. 전체 범위가 검수 대상)";
    private static final String NO_FIGMA_FRAME = "(이번 스프린트 Figma 프레임 없음. 화면 요소를 추측하지 말 것)";
    private static final String DEFAULT_STATE = "기본";
    private static final String LINE_BREAK = "\n";
    private static final String DELIMITER = ", ";
    private static final String LIST_LINE = "- %s: %s";
    private static final String SCREEN_LINE = "- %s: 프레임 %d개, 상태 [%s]";
    private static final String REPO_HEADER = "### %s";
    private static final String FILE_LINE = "- %s %s";
    private static final String RENAMED_FILE_LINE = "- %s %s (이전 %s)";

    private final TestCatalog testCatalog;
    private final LlmPromptTemplates llmPromptTemplates;

    private final ObjectMapper objectMapper;
    private final SetupProps setupProps;

    public SetupPromptDto build(
            NotionSprintDto sprint,
            SetupFeatureSpecDto spec,
            List<FigmaScreenDto> screens,
            OpenApiExcerptDto excerpt,
            SetupDiffDto diff
    ) {
        String system = llmPromptTemplates.render(SYSTEM_TEMPLATE, Map.of(
                "personas", personas(),
                "testCatalog", testCatalog(spec.screenId())
        ));
        String user = llmPromptTemplates.render(USER_TEMPLATE, Map.ofEntries(
                Map.entry("screenId", spec.screenId()),
                Map.entry("name", orNone(spec.spec().name())),
                Map.entry("route", orNone(spec.spec().route())),
                Map.entry("parts", spec.parts().stream().map(Part::name).collect(Collectors.joining(DELIMITER))),
                Map.entry("specStatus", spec.specStatus().name() + " (" + spec.specStatus().getNotionLabel() + ")"),
                Map.entry("specMarkdown", orNone(spec.bodyMarkdown())),
                Map.entry("figmaStates", figmaStates(screens)),
                Map.entry("changeSummary", orNone(sprint.changeSummary())),
                Map.entry("openApiExcerpt", openApiExcerpt(excerpt)),
                Map.entry("changedFiles", changedFiles(diff))
        ));

        int maxChars = setupProps.maxPromptChars();
        if (user.length() <= maxChars) {
            return SetupPromptDto.of(new LlmPromptDto(system, user), false);
        }
        String truncated = user.substring(0, maxChars - TRUNCATION_NOTICE.length()) + TRUNCATION_NOTICE;
        return SetupPromptDto.of(new LlmPromptDto(system, truncated), true);
    }

    private String personas() {
        return Arrays.stream(PersonaKey.values())
                .map(persona -> LIST_LINE.formatted(persona.getKey(), persona.getDescription()))
                .collect(Collectors.joining(LINE_BREAK));
    }

    private String testCatalog(String screenId) {
        List<TestCatalogEntryDto> entries = testCatalog.findByScreenId(screenId);
        if (entries.isEmpty()) {
            return NO_TEST_CATALOG;
        }
        return entries.stream()
                .map(entry -> LIST_LINE.formatted(entry.id(), entry.title()))
                .collect(Collectors.joining(LINE_BREAK));
    }

    private String figmaStates(List<FigmaScreenDto> screens) {
        if (screens.isEmpty()) {
            return NO_FIGMA_FRAME;
        }
        return screens.stream()
                .map(screen -> SCREEN_LINE.formatted(
                        screen.platform(),
                        screen.frames().size(),
                        screen.states().isEmpty() ? DEFAULT_STATE : String.join(DELIMITER, screen.states())
                ))
                .collect(Collectors.joining(LINE_BREAK));
    }

    private String openApiExcerpt(OpenApiExcerptDto excerpt) {
        if (excerpt == null || excerpt.foundOperationIds().isEmpty()) {
            return NONE;
        }
        try {
            return objectMapper.writeValueAsString(excerpt.excerpt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("OpenAPI 발췌를 프롬프트로 바꿀 수 없습니다.", e);
        }
    }

    private String changedFiles(SetupDiffDto diff) {
        if (diff.isEmpty()) {
            return NO_PREVIOUS_BASELINE;
        }
        return diff.repoToFiles().entrySet().stream()
                .map(entry -> REPO_HEADER.formatted(entry.getKey()) + LINE_BREAK + fileLines(entry.getValue()))
                .collect(Collectors.joining(LINE_BREAK + LINE_BREAK));
    }

    private String fileLines(List<GithubChangedFileDto> files) {
        if (files.isEmpty()) {
            return NONE;
        }
        return files.stream()
                .map(file -> StringUtils.hasText(file.previousFilename())
                        ? RENAMED_FILE_LINE.formatted(file.status(), file.filename(), file.previousFilename())
                        : FILE_LINE.formatted(file.status(), file.filename()))
                .collect(Collectors.joining(LINE_BREAK));
    }

    private String orNone(String value) {
        return StringUtils.hasText(value) ? value.trim() : NONE;
    }
}
