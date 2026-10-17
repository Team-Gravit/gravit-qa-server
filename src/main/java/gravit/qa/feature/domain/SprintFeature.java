package gravit.qa.feature.domain;

import gravit.qa.sprint.dto.internal.WarningDto;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", nullable = false)
    private List<WarningDto> warnings;

    @Builder(access = AccessLevel.PRIVATE)
    private SprintFeature(
            Long sprintId,
            Long featureId,
            List<String> figmaNodeIds,
            List<WarningDto> warnings
    ) {
        this.sprintId = sprintId;
        this.featureId = featureId;
        this.figmaNodeIds = figmaNodeIds;
        this.warnings = warnings;
    }

    public static SprintFeature create(
            long sprintId,
            long featureId,
            List<String> figmaNodeIds,
            List<WarningDto> warnings
    ) {
        return SprintFeature.builder()
                .sprintId(sprintId)
                .featureId(featureId)
                .figmaNodeIds(List.copyOf(figmaNodeIds))
                .warnings(List.copyOf(warnings))
                .build();
    }
}
