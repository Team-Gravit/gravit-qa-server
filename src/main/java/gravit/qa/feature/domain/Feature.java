package gravit.qa.feature.domain;

import gravit.qa.feature.dto.internal.FeatureUpsertDto;
import gravit.qa.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "feature")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feature extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "screen_id", nullable = false, length = 100)
    private String screenId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "route")
    private String route;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "parts", nullable = false)
    private List<String> parts;

    @Column(name = "notion_page_id", nullable = false, length = 64)
    private String notionPageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "spec_status", nullable = false)
    private SpecStatus specStatus;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "related_operation_ids", nullable = false)
    private List<String> relatedOperationIds;

    @Builder(access = AccessLevel.PRIVATE)
    private Feature(
            String screenId,
            String name,
            String route,
            List<String> parts,
            String notionPageId,
            SpecStatus specStatus,
            List<String> relatedOperationIds
    ) {
        this.screenId = screenId;
        this.name = name;
        this.route = route;
        this.parts = parts;
        this.notionPageId = notionPageId;
        this.specStatus = specStatus;
        this.relatedOperationIds = relatedOperationIds;
    }

    public static Feature create(FeatureUpsertDto feature) {
        return Feature.builder()
                .screenId(feature.screenId())
                .name(feature.name())
                .route(feature.route())
                .parts(Part.toNames(feature.parts()))
                .notionPageId(feature.notionPageId())
                .specStatus(feature.specStatus())
                .relatedOperationIds(List.copyOf(feature.relatedOperationIds()))
                .build();
    }

    public List<Part> getParts() {
        return Part.fromNames(parts);
    }

    public void update(FeatureUpsertDto feature) {
        this.screenId = feature.screenId();
        this.name = feature.name();
        this.route = feature.route();
        this.parts = Part.toNames(feature.parts());
        this.notionPageId = feature.notionPageId();
        this.specStatus = feature.specStatus();
        this.relatedOperationIds = List.copyOf(feature.relatedOperationIds());
    }
}
