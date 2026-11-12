package gravit.qa.autoReview.support;

import gravit.qa.adapter.exception.LlmOutputInvalidException;
import gravit.qa.adapter.llm.dto.internal.LlmPromptDto;
import gravit.qa.adapter.llm.infrastructure.LlmClient;
import gravit.qa.adapter.llm.support.LlmPromptTemplates;
import gravit.qa.autoReview.dto.internal.AutoReviewJudgmentDto;
import gravit.qa.autoReview.dto.internal.AutoReviewOutputDto;
import gravit.qa.autoReview.dto.internal.AutoReviewResultDto;
import gravit.qa.autoReview.dto.internal.AutoReviewResultOutputDto;
import gravit.qa.autoReview.dto.internal.CodeChunkDto;
import gravit.qa.autoReview.dto.internal.CodeChunksDto;
import gravit.qa.autoReview.dto.internal.CodeEvidenceOutputDto;
import gravit.qa.autoReview.dto.internal.CodeSelectionDto;
import gravit.qa.autoReview.dto.internal.SourceFileDto;
import gravit.qa.checklist.domain.Verdict;
import gravit.qa.checklist.dto.internal.ChecklistItemDto;
import gravit.qa.checklist.dto.internal.CodeEvidenceDto;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.dto.internal.SpecRowDto;
import gravit.qa.feature.dto.internal.SprintFeatureDto;
import gravit.qa.job.support.JobContext;
import gravit.qa.sprint.config.PrepareProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoReviewer {

    public static final String NO_CODE_REASON = "판정 불가: 관련 코드를 찾지 못했습니다(파트 %s, 선택 규칙에 맞는 파일 없음)";

    private static final String SYSTEM_TEMPLATE = "auto-review-system";
    private static final String USER_TEMPLATE = "auto-review-user";
    private static final String CODE_PLACEHOLDER = "{{code}}";
    private static final String UNKNOWN_PREFIX = "판정 불가: ";
    private static final String INVALID_OUTPUT_REASON = "판정 불가: 1차 판정 출력이 검증을 통과하지 못했습니다";
    private static final String ITEM_BLOCK = "[항목 %d] %s%n- 설명: %s%n- 관련 명세 행: %s";
    private static final String NONE = "(없음)";
    private static final String LINE_BREAK = "\n";
    private static final String DELIMITER = ", ";
    private static final int PROMPT_MARGIN = 500;
    private static final int MIN_CODE_BUDGET = 2000;

    private final LlmClient llmClient;
    private final LlmPromptTemplates llmPromptTemplates;

    private final PrepareProps prepareProps;

    public AutoReviewResultDto review(
            SprintFeatureDto feature,
            Part part,
            List<ChecklistItemDto> items,
            CodeSelectionDto selection,
            JobContext context
    ) {
        if (selection.isEmpty()) {
            List<AutoReviewJudgmentDto> judgments = items.stream()
                    .map(item -> AutoReviewJudgmentDto.unknown(item.id(), NO_CODE_REASON.formatted(part)))
                    .toList();
            return AutoReviewResultDto.of(judgments, 0, 0, 0, List.of(), false);
        }

        String template = llmPromptTemplates.render(USER_TEMPLATE, Map.of(
                "screenId", feature.screenId(),
                "name", feature.feature().name(),
                "part", part.name(),
                "commits", selection.repoToCommitSha().entrySet().stream().map(entry -> entry.getKey() + "@" + entry.getValue()).collect(Collectors.joining(DELIMITER)),
                "items", itemBlocks(items, feature.specRows()),
                "code", CODE_PLACEHOLDER
        ));
        int budget = Math.max(MIN_CODE_BUDGET, prepareProps.autoReviewMaxPromptChars() - template.length() - PROMPT_MARGIN);
        CodeChunksDto chunks = CodeChunker.chunk(selection.files(), budget, prepareProps.autoReviewMaxChunks());
        String system = llmPromptTemplates.render(SYSTEM_TEMPLATE, Map.of());
        List<Long> itemIds = items.stream().map(ChecklistItemDto::id).toList();

        Map<Long, List<AutoReviewJudgmentDto>> itemIdToJudgments = new LinkedHashMap<>();
        int promptChars = 0;
        boolean failed = false;
        for (int index = 0; index < chunks.chunks().size(); index++) {
            context.checkCancelled();
            CodeChunkDto chunk = chunks.chunks().get(index);
            String user = template.replace(CODE_PLACEHOLDER, chunk.rendered());
            promptChars += user.length();

            try {
                AutoReviewOutputDto output = llmClient.generate(
                        new LlmPromptDto(system, user),
                        AutoReviewOutputDto.class,
                        value -> AutoReviewValidator.validate(value, itemIds, chunk.slices())
                );
                for (AutoReviewResultOutputDto result : output.results()) {
                    itemIdToJudgments.computeIfAbsent(result.itemId(), id -> new ArrayList<>())
                            .add(AutoReviewJudgmentDto.of(result.itemId(), result.verdict(), reasonOf(result), evidenceOf(result, chunk.slices())));
                }
            } catch (LlmOutputInvalidException e) {
                log.warn("1차 판정 출력 검증 실패. screenId={}, part={}, chunk={}, errors={}", feature.screenId(), part, index + 1, e.getErrors());
                failed = true;
                itemIds.forEach(itemId -> itemIdToJudgments.computeIfAbsent(itemId, id -> new ArrayList<>())
                        .add(AutoReviewJudgmentDto.unknown(itemId, INVALID_OUTPUT_REASON)));
            }
        }

        List<AutoReviewJudgmentDto> merged = itemIds.stream()
                .map(itemId -> merge(itemId, itemIdToJudgments.getOrDefault(itemId, List.of())))
                .toList();
        return AutoReviewResultDto.of(merged, chunks.chunks().size(), selection.files().size(), promptChars, chunks.droppedFiles(), failed);
    }

    static AutoReviewJudgmentDto merge(
            long itemId,
            List<AutoReviewJudgmentDto> judgments
    ) {
        for (Verdict verdict : List.of(Verdict.FAIL, Verdict.PASS)) {
            for (AutoReviewJudgmentDto judgment : judgments) {
                if (judgment.verdict() == verdict) {
                    return judgment;
                }
            }
        }
        if (judgments.isEmpty()) {
            return AutoReviewJudgmentDto.unknown(itemId, INVALID_OUTPUT_REASON);
        }
        return judgments.getLast();
    }

    private String reasonOf(AutoReviewResultOutputDto result) {
        String prefix = result.verdict() == Verdict.UNKNOWN ? UNKNOWN_PREFIX : "";
        return prefix + result.reason().trim();
    }

    static List<CodeEvidenceDto> evidenceOf(
            AutoReviewResultOutputDto result,
            List<SourceFileDto> slices
    ) {
        List<CodeEvidenceOutputDto> evidence = result.evidence() == null ? List.of() : result.evidence();
        return evidence.stream()
                .map(location -> CodeEvidenceDto.of(labelOf(location.path().trim(), slices), location.startLine(), location.endLine()))
                .toList();
    }

    private static String labelOf(
            String path,
            List<SourceFileDto> slices
    ) {
        return slices.stream()
                .filter(slice -> slice.label().equals(path) || slice.path().equals(path))
                .map(SourceFileDto::label)
                .findFirst()
                .orElse(path);
    }

    private String itemBlocks(
            List<ChecklistItemDto> items,
            List<SpecRowDto> rows
    ) {
        Map<String, SpecRowDto> rowIdToRow = new LinkedHashMap<>();
        rows.forEach(row -> rowIdToRow.put(row.rowId(), row));
        return items.stream()
                .map(item -> ITEM_BLOCK.formatted(
                        item.id(),
                        item.title(),
                        item.expected(),
                        item.specRowIds().isEmpty() ? NONE : item.specRowIds().stream()
                                .map(rowId -> rowIdToRow.containsKey(rowId) ? rowIdToRow.get(rowId).text() : rowId)
                                .collect(Collectors.joining(" / "))
                ))
                .collect(Collectors.joining(LINE_BREAK + LINE_BREAK));
    }
}
