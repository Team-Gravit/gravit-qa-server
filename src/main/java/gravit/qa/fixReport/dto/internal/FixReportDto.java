package gravit.qa.fixReport.dto.internal;

import gravit.qa.sprint.domain.ProgressPart;

import java.util.List;

public record FixReportDto(
        String sprintKey,
        ProgressPart part,
        String repoName,
        String ref,
        String figmaFileKey,
        List<FixReportScreenDto> screens
) {

    public static FixReportDto code(
            String sprintKey,
            ProgressPart part,
            String repoName,
            String ref,
            List<FixReportScreenDto> screens
    ) {
        return new FixReportDto(sprintKey, part, repoName, ref, null, List.copyOf(screens));
    }

    public static FixReportDto design(
            String sprintKey,
            String figmaFileKey,
            List<FixReportScreenDto> screens
    ) {
        return new FixReportDto(sprintKey, ProgressPart.DESIGN, null, null, figmaFileKey, List.copyOf(screens));
    }

    public int entryCount() {
        return screens.stream().mapToInt(screen -> screen.entries().size()).sum();
    }
}
