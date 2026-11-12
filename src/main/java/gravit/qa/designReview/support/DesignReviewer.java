package gravit.qa.designReview.support;

import gravit.qa.adapter.exception.LlmOutputInvalidException;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameDto;
import gravit.qa.adapter.llm.dto.internal.LlmPromptDto;
import gravit.qa.adapter.llm.infrastructure.LlmClient;
import gravit.qa.adapter.llm.support.LlmPromptLimiter;
import gravit.qa.adapter.llm.support.LlmPromptTemplates;
import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.Verdict;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.dto.internal.DesignReviewItemDraftDto;
import gravit.qa.designReview.dto.internal.DesignReviewItemOutputDto;
import gravit.qa.designReview.dto.internal.DesignReviewOutputDto;
import gravit.qa.designReview.dto.internal.DesignReviewResultDto;
import gravit.qa.feature.domain.SpecSection;
import gravit.qa.feature.dto.internal.SpecRowDto;
import gravit.qa.feature.dto.internal.SprintFeatureDto;
import gravit.qa.sprint.config.PrepareProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class DesignReviewer {

    public static final String FRAME_MISSING_TITLE = "%s 시안 프레임이 없다";
    public static final String FAILED_TITLE = "디자인 검수 실패";
    public static final String FRAME_MISSING_REASON = "Figma 프레임 없음";

    private static final String SYSTEM_TEMPLATE = "design-review-system";
    private static final String USER_TEMPLATE = "design-review-user";
    private static final String FRAME_MISSING_EXPECTED = "QA 페이지의 이번 스프린트 %s Section에 이 화면 프레임이 없어 시안을 명세와 비교하지 못했습니다. 프레임을 확인한 뒤 사람이 판정한다";
    private static final String FAILED_EXPECTED = "LLM 출력이 검증을 통과하지 못해 자동 비교를 하지 못했습니다. 시안과 명세를 사람이 비교한다";
    private static final String UNKNOWN_PREFIX = "판정 불가: ";
    private static final String ROW_SUFFIX = " (%s-%s)";
    private static final String PLATFORM_HEADER = "## %s 플랫폼";
    private static final String ROWS_HEADER = "### 명세 C행";
    private static final String FRAMES_HEADER = "### Figma 프레임 레이어";
    private static final String LINE_BREAK = "\n";
    private static final String DELIMITER = ", ";
    private static final String ERROR_DELIMITER = "; ";
    private static final Pattern NODE_ID = Pattern.compile("\\[(I?\\d+:\\d+(?:;\\d+:\\d+)*)]");
    private static final int MAX_ERROR_SUMMARY_LENGTH = 500;

    private final LlmClient llmClient;
    private final LlmPromptTemplates llmPromptTemplates;

    private final PrepareProps prepareProps;

    public DesignReviewResultDto review(
            SprintFeatureDto feature,
            Map<DesignPlatform, List<FigmaFrameDto>> platformToFrames,
            Map<String, String> nodeIdToOutline
    ) {
        Map<DesignPlatform, List<SpecRowDto>> platformToRows = rowsByPlatform(feature);
        List<DesignReviewItemDraftDto> items = new ArrayList<>();
        Map<DesignPlatform, String> platformToSection = new EnumMap<>(DesignPlatform.class);

        platformToRows.forEach((platform, rows) -> {
            List<FigmaFrameDto> frames = platformToFrames.getOrDefault(platform, List.of());
            if (frames.isEmpty()) {
                items.add(frameMissing(feature, platform, rows));
                return;
            }
            platformToSection.put(platform, section(platform, rows, frames, nodeIdToOutline));
        });
        if (platformToSection.isEmpty()) {
            return DesignReviewResultDto.of(items, 0, 0, false, false);
        }

        String system = llmPromptTemplates.render(SYSTEM_TEMPLATE, Map.of());
        String combined = user(feature, String.join(LINE_BREAK + LINE_BREAK, platformToSection.values()));
        List<Map<DesignPlatform, String>> calls = combined.length() <= prepareProps.designReviewMaxPromptChars()
                ? List.of(platformToSection)
                : platformToSection.entrySet().stream().map(entry -> Map.of(entry.getKey(), entry.getValue())).toList();

        int promptChars = 0;
        boolean truncated = false;
        boolean failed = false;
        for (Map<DesignPlatform, String> call : calls) {
            String user = user(feature, String.join(LINE_BREAK + LINE_BREAK, call.values()));
            String limited = LlmPromptLimiter.limit(user, prepareProps.designReviewMaxPromptChars());
            promptChars += limited.length();
            truncated |= limited.length() < user.length();

            Map<DesignPlatform, List<String>> platformToRowIds = new EnumMap<>(DesignPlatform.class);
            Map<DesignPlatform, Set<String>> platformToNodeIds = new EnumMap<>(DesignPlatform.class);
            call.keySet().forEach(platform -> {
                platformToRowIds.put(platform, platformToRows.get(platform).stream().map(SpecRowDto::rowId).toList());
                platformToNodeIds.put(platform, nodeIdsOf(call.get(platform)));
            });

            try {
                DesignReviewOutputDto output = llmClient.generate(
                        new LlmPromptDto(system, limited),
                        DesignReviewOutputDto.class,
                        value -> DesignReviewValidator.validate(value, platformToRowIds, platformToNodeIds)
                );
                output.items().forEach(item -> items.add(toDraft(feature, item)));
            } catch (LlmOutputInvalidException e) {
                log.warn("디자인 검수 출력 검증 실패. screenId={}, errors={}", feature.screenId(), e.getErrors());
                failed = true;
                call.keySet().forEach(platform -> items.add(failedItem(feature, platform, platformToRows.get(platform), e)));
            }
        }
        return DesignReviewResultDto.of(items, calls.size(), promptChars, truncated, failed);
    }

    private Map<DesignPlatform, List<SpecRowDto>> rowsByPlatform(SprintFeatureDto feature) {
        Map<DesignPlatform, List<SpecRowDto>> platformToRows = new LinkedHashMap<>();
        for (DesignPlatform platform : DesignPlatform.values()) {
            if (!feature.feature().parts().contains(platform.getPart())) {
                continue;
            }
            List<SpecRowDto> rows = feature.specRows().stream()
                    .filter(row -> row.section() == SpecSection.SCREEN && platform.appliesTo(row))
                    .toList();
            if (!rows.isEmpty()) {
                platformToRows.put(platform, rows);
            }
        }
        return platformToRows;
    }

    private String section(
            DesignPlatform platform,
            List<SpecRowDto> rows,
            List<FigmaFrameDto> frames,
            Map<String, String> nodeIdToOutline
    ) {
        List<String> lines = new ArrayList<>();
        lines.add(PLATFORM_HEADER.formatted(platform));
        lines.add(ROWS_HEADER);
        lines.add("| " + String.join(" | ", rows.getFirst().columns()) + " |");
        rows.forEach(row -> lines.add("| " + String.join(" | ", row.cells().stream().map(cell -> cell.replace(LINE_BREAK, "<br>")).toList()) + " |"));
        lines.add(FRAMES_HEADER);
        for (FigmaFrameDto frame : frames) {
            lines.add(nodeIdToOutline.getOrDefault(frame.nodeId(), "### 프레임 " + frame.name() + " [" + frame.nodeId() + "] (레이어를 읽지 못함)"));
        }
        return String.join(LINE_BREAK, lines);
    }

    private String user(
            SprintFeatureDto feature,
            String sections
    ) {
        return llmPromptTemplates.render(USER_TEMPLATE, Map.of(
                "screenId", feature.screenId(),
                "name", feature.feature().name(),
                "sections", sections
        ));
    }

    private DesignReviewItemDraftDto toDraft(
            SprintFeatureDto feature,
            DesignReviewItemOutputDto item
    ) {
        List<String> rowIds = item.specRowIds().stream().map(String::trim).distinct().toList();
        String reason = (item.verdict() == Verdict.UNKNOWN ? UNKNOWN_PREFIX : "") + item.reason().trim();
        return DesignReviewItemDraftDto.of(
                item.platform(),
                item.title().trim(),
                item.expected().trim() + rowSuffix(feature, rowIds),
                rowIds,
                item.figmaNodeIds() == null ? List.of() : item.figmaNodeIds(),
                item.verdict().toCellState(),
                reason
        );
    }

    private DesignReviewItemDraftDto frameMissing(
            SprintFeatureDto feature,
            DesignPlatform platform,
            List<SpecRowDto> rows
    ) {
        List<String> rowIds = rows.stream().map(SpecRowDto::rowId).toList();
        return DesignReviewItemDraftDto.of(
                platform,
                FRAME_MISSING_TITLE.formatted(platform.getFigmaPlatform()),
                FRAME_MISSING_EXPECTED.formatted(platform.getFigmaPlatform()) + rowSuffix(feature, rowIds),
                rowIds,
                List.of(),
                CellState.UNCHECKED,
                FRAME_MISSING_REASON
        );
    }

    private DesignReviewItemDraftDto failedItem(
            SprintFeatureDto feature,
            DesignPlatform platform,
            List<SpecRowDto> rows,
            LlmOutputInvalidException exception
    ) {
        List<String> rowIds = rows.stream().map(SpecRowDto::rowId).toList();
        String errors = String.join(ERROR_DELIMITER, exception.getErrors());
        String summary = errors.length() <= MAX_ERROR_SUMMARY_LENGTH ? errors : errors.substring(0, MAX_ERROR_SUMMARY_LENGTH);
        return DesignReviewItemDraftDto.of(platform, FAILED_TITLE, FAILED_EXPECTED + rowSuffix(feature, rowIds), rowIds, List.of(), CellState.UNCHECKED, summary);
    }

    private String rowSuffix(
            SprintFeatureDto feature,
            List<String> rowIds
    ) {
        return ROW_SUFFIX.formatted(feature.screenId(), String.join(DELIMITER, rowIds));
    }

    private Set<String> nodeIdsOf(String section) {
        Set<String> nodeIds = new LinkedHashSet<>();
        Matcher matcher = NODE_ID.matcher(section);
        while (matcher.find()) {
            nodeIds.add(matcher.group(1));
        }
        return nodeIds;
    }
}
