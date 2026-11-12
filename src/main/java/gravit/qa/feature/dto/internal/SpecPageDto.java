package gravit.qa.feature.dto.internal;

import gravit.qa.adapter.notion.dto.internal.NotionHistoryRowDto;
import gravit.qa.adapter.notion.dto.internal.NotionHistoryTableDto;
import gravit.qa.adapter.notion.dto.internal.NotionPageBodyDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecTableDto;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;
import org.springframework.util.StringUtils;

import java.util.List;

public record SpecPageDto(
        NotionSpecDto spec,
        NotionPageBodyDto body,
        NotionSpecTableDto table,
        List<Part> parts,
        SpecStatus specStatus,
        List<SpecRowDto> specRows,
        NotionHistoryTableDto history,
        String specMarkdown
) {

    public String screenId() {
        return spec.screenId();
    }

    public String name() {
        return StringUtils.hasText(spec.name()) ? spec.name().trim() : spec.screenId();
    }

    public List<NotionHistoryRowDto> historyRowsOf(String sprintKey) {
        return history.rows().stream()
                .filter(row -> row.belongsTo(sprintKey))
                .toList();
    }

    public List<NotionHistoryRowDto> rowsToReflect(String sprintKey) {
        return historyRowsOf(sprintKey).stream()
                .filter(row -> !row.isReflected())
                .toList();
    }

    public boolean isChanged(String sprintKey) {
        return !historyRowsOf(sprintKey).isEmpty();
    }

    public FeatureUpsertDto toUpsertDto() {
        return FeatureUpsertDto.of(
                spec.screenId(),
                name(),
                table.route(),
                parts,
                spec.pageId(),
                specStatus,
                table.relatedApis()
        );
    }

    public SprintFeatureSpecDto toSprintFeatureSpec(
            long featureId,
            String sprintKey
    ) {
        return SprintFeatureSpecDto.of(
                featureId,
                isChanged(sprintKey),
                specMarkdown,
                specRows,
                history.rows().stream()
                        .map(row -> SpecHistoryRowDto.of(row.sprint(), row.change(), row.reflectedOn()))
                        .toList()
        );
    }
}
