package gravit.qa.feature.domain;

import gravit.qa.feature.dto.internal.SpecHistoryRowDto;
import gravit.qa.feature.dto.internal.SpecRowDto;
import gravit.qa.feature.dto.internal.SprintFeatureSpecDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Getter
@Table(name = "sprint_feature")
@IdClass(SprintFeatureId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SprintFeature {

    @Id
    @Column(name = "sprint_id")
    private Long sprintId;

    @Id
    @Column(name = "feature_id")
    private Long featureId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "figma_node_ids", nullable = false)
    private List<String> figmaNodeIds;

    @Column(name = "changed", nullable = false)
    private boolean changed;

    @Column(name = "spec_markdown", nullable = false)
    private String specMarkdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spec_rows", nullable = false)
    private List<SpecRowDto> specRows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "history_rows", nullable = false)
    private List<SpecHistoryRowDto> historyRows;

    @Builder(access = AccessLevel.PRIVATE)
    private SprintFeature(
            Long sprintId,
            Long featureId,
            List<String> figmaNodeIds,
            boolean changed,
            String specMarkdown,
            List<SpecRowDto> specRows,
            List<SpecHistoryRowDto> historyRows
    ) {
        this.sprintId = sprintId;
        this.featureId = featureId;
        this.figmaNodeIds = figmaNodeIds;
        this.changed = changed;
        this.specMarkdown = specMarkdown;
        this.specRows = specRows;
        this.historyRows = historyRows;
    }

    public static SprintFeature create(
            long sprintId,
            SprintFeatureSpecDto spec
    ) {
        return SprintFeature.builder()
                .sprintId(sprintId)
                .featureId(spec.featureId())
                .figmaNodeIds(List.of())
                .changed(spec.changed())
                .specMarkdown(spec.specMarkdown())
                .specRows(List.copyOf(spec.specRows()))
                .historyRows(List.copyOf(spec.historyRows()))
                .build();
    }

    public void updateSpec(SprintFeatureSpecDto spec) {
        this.changed = spec.changed();
        this.specMarkdown = spec.specMarkdown();
        this.specRows = List.copyOf(spec.specRows());
        this.historyRows = List.copyOf(spec.historyRows());
    }

    public void updateFigmaNodeIds(List<String> figmaNodeIds) {
        this.figmaNodeIds = List.copyOf(figmaNodeIds);
    }
}
