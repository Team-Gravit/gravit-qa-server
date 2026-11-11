package gravit.qa.reflection.support;

import gravit.qa.adapter.exception.LlmOutputInvalidException;
import gravit.qa.adapter.llm.dto.internal.LlmPromptDto;
import gravit.qa.adapter.llm.infrastructure.LlmClient;
import gravit.qa.adapter.llm.support.LlmPromptLimiter;
import gravit.qa.adapter.llm.support.LlmPromptTemplates;
import gravit.qa.adapter.notion.dto.internal.NotionHistoryRowDto;
import gravit.qa.adapter.notion.support.NotionBlockRenderer;
import gravit.qa.feature.dto.internal.SpecPageDto;
import gravit.qa.reflection.domain.ReflectionOperationType;
import gravit.qa.reflection.dto.internal.ReflectionHistoryRowDto;
import gravit.qa.reflection.dto.internal.ReflectionOperationDto;
import gravit.qa.reflection.dto.internal.ReflectionOperationOutputDto;
import gravit.qa.reflection.dto.internal.ReflectionOutputDto;
import gravit.qa.reflection.dto.internal.ReflectionProposalDto;
import gravit.qa.reflection.dto.internal.SpecBlockEntryDto;
import gravit.qa.sprint.config.PrepareProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReflectionProposer {

    private static final String SYSTEM_TEMPLATE = "spec-reflection-system";
    private static final String USER_TEMPLATE = "spec-reflection-user";
    private static final String HISTORY_LINE = "%d. %s";
    private static final String LINE_BREAK = "\n";
    private static final String ERROR_DELIMITER = "; ";
    private static final String FAILED_MESSAGE = "반영 제안을 만들지 못했습니다. 명세를 고친 뒤 다시 수집하거나 거절하세요. 오류: ";
    private static final int MAX_ERROR_SUMMARY_LENGTH = 500;

    private final LlmClient llmClient;
    private final LlmPromptTemplates llmPromptTemplates;

    private final PrepareProps prepareProps;

    public ReflectionProposalDto propose(
            SpecPageDto page,
            List<NotionHistoryRowDto> rows,
            String sprintKey
    ) {
        SpecBlockIndex index = SpecBlockIndex.of(page.spec().pageId(), page.body().blocks(), page.history());
        List<ReflectionHistoryRowDto> historyRows = rows.stream().map(ReflectionHistoryRowDto::from).toList();
        String before = NotionBlockRenderer.toMarkdown(page.body().blocks());

        String user = llmPromptTemplates.render(USER_TEMPLATE, Map.of(
                "screenId", page.screenId(),
                "name", page.name(),
                "sprintKey", sprintKey,
                "historyRows", historyLines(rows),
                "blocks", index.render()
        ));
        String limited = LlmPromptLimiter.limit(user, prepareProps.reflectionMaxPromptChars());
        LlmPromptDto prompt = new LlmPromptDto(llmPromptTemplates.render(SYSTEM_TEMPLATE, Map.of()), limited);

        try {
            ReflectionOutputDto output = llmClient.generate(
                    prompt,
                    ReflectionOutputDto.class,
                    value -> ReflectionValidator.validate(value, index, rows.size())
            );
            List<ReflectionOperationDto> operations = toOperations(output.operations(), index);
            String after = NotionBlockRenderer.toMarkdown(index.apply(operations));
            return ReflectionProposalDto.proposed(historyRows, operations, baseBlocks(operations, index), before, after, limited.length());
        } catch (LlmOutputInvalidException e) {
            log.warn("반영 제안 출력 검증 실패. screenId={}, errors={}", page.screenId(), e.getErrors());
            String errors = String.join(ERROR_DELIMITER, e.getErrors());
            String summary = errors.length() <= MAX_ERROR_SUMMARY_LENGTH ? errors : errors.substring(0, MAX_ERROR_SUMMARY_LENGTH);
            return ReflectionProposalDto.failed(historyRows, before, FAILED_MESSAGE + summary, limited.length());
        }
    }

    private List<ReflectionOperationDto> toOperations(
            List<ReflectionOperationOutputDto> outputs,
            SpecBlockIndex index
    ) {
        List<ReflectionOperationDto> operations = new ArrayList<>();
        for (ReflectionOperationOutputDto output : outputs) {
            SpecBlockEntryDto target = index.find(output.target()).orElseThrow();
            operations.add(ReflectionOperationDto.of(
                    output.historyRow(),
                    output.op(),
                    target.key(),
                    target.blockId(),
                    parentOf(output.op(), target),
                    blockTypeOf(output, target),
                    output.text() == null ? null : output.text().trim(),
                    output.cells() == null ? null : output.cells().stream().map(String::trim).toList(),
                    target.tableWidth()
            ));
        }
        return operations;
    }

    private String parentOf(
            ReflectionOperationType type,
            SpecBlockEntryDto target
    ) {
        return switch (type) {
            case INSERT_BLOCK_AFTER, INSERT_ROW_AFTER -> target.parentId();
            default -> null;
        };
    }

    private String blockTypeOf(
            ReflectionOperationOutputDto output,
            SpecBlockEntryDto target
    ) {
        return switch (output.op()) {
            case INSERT_BLOCK_AFTER -> output.blockType();
            case INSERT_ROW_AFTER -> "table_row";
            default -> target.type();
        };
    }

    private Map<String, String> baseBlocks(
            List<ReflectionOperationDto> operations,
            SpecBlockIndex index
    ) {
        Map<String, String> blockIdToEditedTime = new LinkedHashMap<>();
        for (ReflectionOperationDto operation : operations) {
            index.findByBlockId(operation.targetBlockId())
                    .filter(entry -> entry.lastEditedTime() != null)
                    .ifPresent(entry -> blockIdToEditedTime.put(entry.blockId(), entry.lastEditedTime()));
        }
        return blockIdToEditedTime;
    }

    private String historyLines(List<NotionHistoryRowDto> rows) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            lines.add(HISTORY_LINE.formatted(index + 1, rows.get(index).change()));
        }
        return String.join(LINE_BREAK, lines);
    }
}
