package gravit.qa.sprint.support;

import gravit.qa.adapter.exception.LlmOutputInvalidException;
import gravit.qa.adapter.llm.infrastructure.LlmClient;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.ChecklistDraftItemOutputDto;
import gravit.qa.checklist.dto.internal.ChecklistDraftOutputDto;
import gravit.qa.checklist.dto.internal.ChecklistItemDraftDto;
import gravit.qa.checklist.dto.internal.ClassificationResultDto;
import gravit.qa.checklist.support.ChecklistClassifier;
import gravit.qa.checklist.support.ChecklistDraftValidator;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;
import gravit.qa.job.support.JobContext;
import gravit.qa.sprint.config.SetupProps;
import gravit.qa.sprint.domain.WarningCode;
import gravit.qa.sprint.dto.internal.SetupDiffDto;
import gravit.qa.sprint.dto.internal.SetupFeatureDraftDto;
import gravit.qa.sprint.dto.internal.SetupFeatureSpecDto;
import gravit.qa.sprint.dto.internal.SetupFigmaDto;
import gravit.qa.sprint.dto.internal.SetupNotionDto;
import gravit.qa.sprint.dto.internal.SetupOpenApiDto;
import gravit.qa.sprint.dto.internal.SetupPromptDto;
import gravit.qa.sprint.dto.internal.WarningDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SetupInterpreter {

    public static final String FAILED_ITEM_TITLE = "초안 생성 실패";

    private static final String FAILED_ITEM_EXPECTED = "LLM 출력이 출력 계약을 지키지 않아 초안을 만들지 못했습니다. 이 기능의 체크 항목을 직접 추가하고 이 항목은 거절하세요. 오류: ";
    private static final String SPEC_NEEDS_CHECK_PREFIX = "근거 없음(명세 확인 필요) - ";
    private static final String FRAME_MISSING_PREFIX = "근거 없음(Figma %s 프레임 없음) - ";
    private static final String ITEM_GENERATION_FAILED_MESSAGE = "LLM 출력이 검증을 통과하지 못해 초안 생성 실패 항목으로 대신했습니다";
    private static final String LLM_INPUT_TRUNCATED_MESSAGE = "프롬프트가 입력 상한(%d자)을 넘어 뒤쪽 입력을 잘랐습니다";
    private static final String ERROR_DELIMITER = "; ";
    private static final int MAX_ERROR_SUMMARY_LENGTH = 500;

    private final SetupPromptBuilder setupPromptBuilder;
    private final ChecklistDraftValidator checklistDraftValidator;
    private final ChecklistClassifier checklistClassifier;
    private final LlmClient llmClient;

    private final SetupProps setupProps;

    public List<SetupFeatureDraftDto> interpret(
            SetupNotionDto notion,
            SetupFigmaDto figma,
            SetupDiffDto diff,
            SetupOpenApiDto openApi,
            JobContext context
    ) {
        List<SetupFeatureDraftDto> features = new ArrayList<>();
        for (SetupFeatureSpecDto spec : notion.specs()) {
            context.checkCancelled();
            features.add(interpretFeature(spec, notion, figma, diff, openApi));
        }
        return features;
    }

    private SetupFeatureDraftDto interpretFeature(
            SetupFeatureSpecDto spec,
            SetupNotionDto notion,
            SetupFigmaDto figma,
            SetupDiffDto diff,
            SetupOpenApiDto openApi
    ) {
        String screenId = spec.screenId();
        List<WarningDto> warnings = new ArrayList<>();
        warnings.addAll(notion.warningsOf(screenId));
        warnings.addAll(figma.warningsOf(screenId));
        warnings.addAll(openApi.warningsOf(screenId));

        SetupPromptDto prompt = setupPromptBuilder.build(notion.sprint(), spec, figma.screensOf(screenId), openApi.excerptOf(screenId), diff);
        if (prompt.truncated()) {
            warnings.add(WarningDto.of(WarningCode.LLM_INPUT_TRUNCATED, LLM_INPUT_TRUNCATED_MESSAGE.formatted(setupProps.maxPromptChars())));
        }

        List<ChecklistItemDraftDto> items;
        try {
            ChecklistDraftOutputDto output = llmClient.generate(
                    prompt.prompt(),
                    ChecklistDraftOutputDto.class,
                    value -> checklistDraftValidator.validate(value, spec.parts())
            );
            items = output.items().stream()
                    .map(item -> toDraft(item, spec, figma.missingPartsOf(screenId)))
                    .toList();
        } catch (LlmOutputInvalidException e) {
            log.warn("LLM 출력 검증 실패로 초안 생성 실패 항목을 남깁니다. screenId={}, errors={}", screenId, e.getErrors());
            items = List.of(failedItem(spec, e));
            warnings.add(WarningDto.of(WarningCode.ITEM_GENERATION_FAILED, ITEM_GENERATION_FAILED_MESSAGE));
        }

        return SetupFeatureDraftDto.of(spec, figma.nodeIdsOf(screenId), warnings, items);
    }

    private ChecklistItemDraftDto toDraft(
            ChecklistDraftItemOutputDto item,
            SetupFeatureSpecDto spec,
            List<Part> missingParts
    ) {
        ClassificationResultDto classification = checklistClassifier.classify(item.criteria(), item.testRef());
        return ChecklistItemDraftDto.of(
                item.title().trim(),
                basisPrefix(spec, item.parts(), missingParts) + item.expected().trim(),
                item.parts(),
                classification.classification(),
                item.criteria(),
                classification.automationGap(),
                item.testRef(),
                item.personaKey()
        );
    }

    private ChecklistItemDraftDto failedItem(
            SetupFeatureSpecDto spec,
            LlmOutputInvalidException exception
    ) {
        String errors = String.join(ERROR_DELIMITER, exception.getErrors());
        String summary = errors.length() <= MAX_ERROR_SUMMARY_LENGTH ? errors : errors.substring(0, MAX_ERROR_SUMMARY_LENGTH);
        return ChecklistItemDraftDto.of(
                FAILED_ITEM_TITLE,
                FAILED_ITEM_EXPECTED + summary,
                spec.parts(),
                Classification.MANUAL,
                null,
                false,
                null,
                null
        );
    }

    private String basisPrefix(
            SetupFeatureSpecDto spec,
            List<Part> itemParts,
            List<Part> missingParts
    ) {
        StringBuilder prefix = new StringBuilder();
        if (spec.specStatus() == SpecStatus.NEEDS_CHECK) {
            prefix.append(SPEC_NEEDS_CHECK_PREFIX);
        }
        for (Part part : itemParts) {
            if (missingParts.contains(part)) {
                prefix.append(FRAME_MISSING_PREFIX.formatted(SetupCollector.PART_TO_PLATFORM.get(part)));
            }
        }
        return prefix.toString();
    }
}
