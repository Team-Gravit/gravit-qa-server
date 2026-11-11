package gravit.qa.checklist.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.exception.LlmOutputInvalidException;
import gravit.qa.adapter.llm.dto.internal.LlmPromptDto;
import gravit.qa.adapter.llm.infrastructure.LlmClient;
import gravit.qa.adapter.llm.support.LlmPromptLimiter;
import gravit.qa.adapter.llm.support.LlmPromptTemplates;
import gravit.qa.adapter.openapi.dto.internal.OpenApiExcerptDto;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.ChecklistDraftItemOutputDto;
import gravit.qa.checklist.dto.internal.ChecklistDraftOutputDto;
import gravit.qa.checklist.dto.internal.ChecklistGenerationDto;
import gravit.qa.checklist.dto.internal.ChecklistItemDraftDto;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;
import gravit.qa.feature.dto.internal.FeatureDto;
import gravit.qa.feature.dto.internal.SpecRowDto;
import gravit.qa.feature.dto.internal.SprintFeatureDto;
import gravit.qa.sprint.config.PrepareProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChecklistGenerator {

    public static final String FAILED_ITEM_TITLE = "초안 생성 실패";

    private static final String SYSTEM_TEMPLATE = "checklist-draft-system";
    private static final String USER_TEMPLATE = "checklist-draft-user";
    private static final String FAILED_ITEM_EXPECTED = "LLM 출력이 검증을 통과하지 못해 이 화면의 체크 항목을 만들지 못했습니다. 항목을 직접 추가하고 이 항목은 지우세요. 오류: ";
    private static final String SPEC_NEEDS_CHECK_PREFIX = "근거 없음(명세 확인 필요) - ";
    private static final String NONE = "(없음)";
    private static final String NO_ROWS = "(명세 표 행 없음. 본문 내용으로 항목을 만든다)";
    private static final String DELIMITER = ", ";
    private static final String ERROR_DELIMITER = "; ";
    private static final String CHANGED = "예(이번 스프린트 수정 이력 반영 화면)";
    private static final String UNCHANGED = "아니오";
    private static final int MAX_ERROR_SUMMARY_LENGTH = 500;

    private final LlmClient llmClient;
    private final LlmPromptTemplates llmPromptTemplates;

    private final ObjectMapper objectMapper;
    private final PrepareProps prepareProps;

    public ChecklistGenerationDto generate(
            SprintFeatureDto sprintFeature,
            OpenApiExcerptDto excerpt,
            String changeSummary
    ) {
        FeatureDto feature = sprintFeature.feature();
        String user = llmPromptTemplates.render(USER_TEMPLATE, Map.ofEntries(
                Map.entry("screenId", feature.screenId()),
                Map.entry("name", orNone(feature.name())),
                Map.entry("route", orNone(feature.route())),
                Map.entry("parts", feature.parts().stream().map(Part::name).collect(Collectors.joining(DELIMITER))),
                Map.entry("specStatus", feature.specStatus().name() + " (" + feature.specStatus().getNotionLabel() + ")"),
                Map.entry("relatedApis", feature.relatedApis().isEmpty() ? NONE : String.join(DELIMITER, feature.relatedApis())),
                Map.entry("changed", sprintFeature.changed() ? CHANGED : UNCHANGED),
                Map.entry("rowIds", rowIds(sprintFeature.specRows())),
                Map.entry("specMarkdown", orNone(sprintFeature.specMarkdown())),
                Map.entry("changeSummary", orNone(changeSummary)),
                Map.entry("openApiExcerpt", openApiExcerpt(excerpt))
        ));
        String limited = LlmPromptLimiter.limit(user, prepareProps.checklistMaxPromptChars());
        LlmPromptDto prompt = new LlmPromptDto(llmPromptTemplates.render(SYSTEM_TEMPLATE, Map.of()), limited);

        try {
            ChecklistDraftOutputDto output = llmClient.generate(
                    prompt,
                    ChecklistDraftOutputDto.class,
                    value -> ChecklistDraftValidator.validate(value, feature.parts(), sprintFeature.specRows())
            );
            List<ChecklistItemDraftDto> items = output.items().stream()
                    .map(item -> toDraft(item, feature))
                    .toList();
            return ChecklistGenerationDto.of(items, limited.length(), limited.length() < user.length(), false);
        } catch (LlmOutputInvalidException e) {
            log.warn("LLM 출력 검증 실패로 초안 생성 실패 항목을 남깁니다. screenId={}, errors={}", feature.screenId(), e.getErrors());
            return ChecklistGenerationDto.of(List.of(failedItem(feature, e)), limited.length(), limited.length() < user.length(), true);
        }
    }

    private ChecklistItemDraftDto toDraft(
            ChecklistDraftItemOutputDto item,
            FeatureDto feature
    ) {
        String prefix = feature.specStatus() == SpecStatus.NEEDS_CHECK ? SPEC_NEEDS_CHECK_PREFIX : "";
        return ChecklistItemDraftDto.of(
                item.title().trim(),
                prefix + item.expected().trim(),
                item.parts(),
                item.classification(),
                item.specRowIds().stream().map(String::trim).distinct().toList()
        );
    }

    private ChecklistItemDraftDto failedItem(
            FeatureDto feature,
            LlmOutputInvalidException exception
    ) {
        String errors = String.join(ERROR_DELIMITER, exception.getErrors());
        String summary = errors.length() <= MAX_ERROR_SUMMARY_LENGTH ? errors : errors.substring(0, MAX_ERROR_SUMMARY_LENGTH);
        return ChecklistItemDraftDto.of(FAILED_ITEM_TITLE, FAILED_ITEM_EXPECTED + summary, feature.parts(), Classification.MANUAL, List.of());
    }

    private String rowIds(List<SpecRowDto> rows) {
        if (rows.isEmpty()) {
            return NO_ROWS;
        }
        return rows.stream().map(SpecRowDto::rowId).collect(Collectors.joining(DELIMITER));
    }

    private String openApiExcerpt(OpenApiExcerptDto excerpt) {
        if (excerpt == null || excerpt.foundApis().isEmpty()) {
            return NONE;
        }
        try {
            return objectMapper.writeValueAsString(excerpt.excerpt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("OpenAPI 발췌를 프롬프트로 바꿀 수 없습니다.", e);
        }
    }

    private String orNone(String value) {
        return StringUtils.hasText(value) ? value.trim() : NONE;
    }
}
