package gravit.qa.fixReport.support;

import gravit.qa.checklist.dto.internal.CodeEvidenceDto;
import gravit.qa.fixReport.dto.internal.FixReportDto;
import gravit.qa.fixReport.dto.internal.FixReportEntryDto;
import gravit.qa.fixReport.dto.internal.FixReportScreenDto;
import gravit.qa.sprint.domain.ProgressPart;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FixReportWriter {

    private static final String LINE_BREAK = "\n";
    private static final String CRLF = "\r\n";
    private static final String CR = "\r";
    private static final String INDENT = "   ";
    private static final String WORD_SPACE = " ";
    private static final String HYPHEN = "-";
    private static final char NODE_ID_SEPARATOR = ':';
    private static final char NODE_ID_URL_SEPARATOR = '-';

    private static final String TITLE = "%s / %s 수정사항 %d건";
    private static final String CODE_BASELINE = "기준 코드: %s %s";
    private static final String DESIGN_BASELINE = "기준 시안: Figma %s";
    private static final String PRIORITY = "기대 결과 우선순위: Notion 명세 > Figma 시안 > 코드";
    private static final String NOTHING_TO_FIX = "수정할 항목이 없습니다.";
    private static final String SCREEN = "[%s] %s";
    private static final String PLATFORM_SCREEN = "[%s] %s · %s";
    private static final String SPEC = "명세: https://www.notion.so/%s";
    private static final String NUMBER = "%d. ";
    private static final String EXPECTED = INDENT + "기대: ";
    private static final String PROBLEM = INDENT + "문제: ";
    private static final String NO_REASON = "(사유 없음)";
    private static final String SPEC_ROWS = INDENT + "명세 행: ";
    private static final String SPEC_ROW_DELIMITER = ", ";
    private static final String CODE = INDENT + "코드: %s:%d-%d";
    private static final String FIGMA = INDENT + "시안: https://www.figma.com/design/%s?node-id=%s";

    public static String write(FixReportDto report) {
        List<String> lines = new ArrayList<>();
        int count = report.entryCount();

        lines.add(TITLE.formatted(singleLine(report.sprintKey()), report.part().name(), count));
        baseline(report).ifPresent(lines::add);
        if (count == 0) {
            lines.add("");
            lines.add(NOTHING_TO_FIX);
            return toText(lines);
        }
        lines.add(PRIORITY);

        int number = 1;
        for (FixReportScreenDto screen : report.screens()) {
            lines.add("");
            lines.add(screenHeader(screen));
            lines.add(SPEC.formatted(screen.notionPageId().replace(HYPHEN, "")));
            for (FixReportEntryDto entry : screen.entries()) {
                lines.add("");
                addEntry(lines, number++, entry, report.figmaFileKey());
            }
        }
        return toText(lines);
    }

    private static Optional<String> baseline(FixReportDto report) {
        if (report.part() == ProgressPart.DESIGN) {
            return Optional.ofNullable(report.figmaFileKey()).map(DESIGN_BASELINE::formatted);
        }
        if (report.repoName() == null || report.ref() == null) {
            return Optional.empty();
        }
        return Optional.of(CODE_BASELINE.formatted(report.repoName(), report.ref()));
    }

    private static String screenHeader(FixReportScreenDto screen) {
        if (screen.platform() == null) {
            return SCREEN.formatted(singleLine(screen.screenId()), singleLine(screen.screenName()));
        }
        return PLATFORM_SCREEN.formatted(singleLine(screen.screenId()), singleLine(screen.screenName()), screen.platform().getPart().getNotionLabel());
    }

    private static void addEntry(
            List<String> lines,
            int number,
            FixReportEntryDto entry,
            String figmaFileKey
    ) {
        String numberPrefix = NUMBER.formatted(number);
        if (!addValue(lines, numberPrefix, entry.title())) {
            lines.add(numberPrefix.stripTrailing());
        }
        addValue(lines, EXPECTED, entry.expected());
        addValue(lines, PROBLEM, entry.reason() == null ? NO_REASON : entry.reason());
        if (!entry.specRowIds().isEmpty()) {
            lines.add(SPEC_ROWS + String.join(SPEC_ROW_DELIMITER, entry.specRowIds()));
        }
        for (CodeEvidenceDto evidence : entry.evidence()) {
            lines.add(CODE.formatted(evidence.path(), evidence.startLine(), evidence.endLine()));
        }
        if (figmaFileKey != null) {
            for (String nodeId : entry.figmaNodeIds()) {
                lines.add(FIGMA.formatted(figmaFileKey, nodeId.replace(NODE_ID_SEPARATOR, NODE_ID_URL_SEPARATOR)));
            }
        }
    }

    private static boolean addValue(
            List<String> lines,
            String prefix,
            String value
    ) {
        List<String> valueLines = valueLines(value);
        if (valueLines.isEmpty()) {
            return false;
        }
        lines.add(prefix + valueLines.getFirst());
        valueLines.stream().skip(1).forEach(line -> lines.add(INDENT + line));
        return true;
    }

    private static List<String> valueLines(String value) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(normalizeLineBreaks(value.strip()).split(LINE_BREAK))
                .map(String::stripTrailing)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static String singleLine(String value) {
        return String.join(WORD_SPACE, valueLines(value).stream().map(String::strip).toList());
    }

    private static String normalizeLineBreaks(String value) {
        return value.replace(CRLF, LINE_BREAK).replace(CR, LINE_BREAK);
    }

    private static String toText(List<String> lines) {
        return String.join(LINE_BREAK, lines) + LINE_BREAK;
    }
}
