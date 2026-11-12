package gravit.qa.checklist.domain;

import gravit.qa.checklist.dto.internal.ChecklistItemDraftDto;
import gravit.qa.feature.domain.Part;
import gravit.qa.global.entity.BaseEntity;
import gravit.qa.global.exception.domain.RestApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

import static gravit.qa.global.exception.domain.CustomErrorCode.VERSION_CONFLICT;

@Entity
@Getter
@Table(name = "checklist_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_id", nullable = false)
    private long sprintId;

    @Column(name = "feature_id", nullable = false)
    private long featureId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "expected", nullable = false)
    private String expected;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "parts", nullable = false)
    private List<String> parts;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false)
    private Classification classification;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "spec_row_ids", nullable = false)
    private List<String> specRowIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_refs", nullable = false)
    private List<Long> sourceRefs;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private ItemOrigin origin;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Builder(access = AccessLevel.PRIVATE)
    private ChecklistItem(
            long sprintId,
            long featureId,
            String title,
            String expected,
            List<String> parts,
            Classification classification,
            List<String> specRowIds,
            List<Long> sourceRefs,
            ItemOrigin origin,
            int sortOrder
    ) {
        this.sprintId = sprintId;
        this.featureId = featureId;
        this.title = title;
        this.expected = expected;
        this.parts = parts;
        this.classification = classification;
        this.specRowIds = specRowIds;
        this.sourceRefs = sourceRefs;
        this.origin = origin;
        this.sortOrder = sortOrder;
    }

    public static ChecklistItem generate(
            long sprintId,
            long featureId,
            ChecklistItemDraftDto draft,
            List<Long> sourceRefs,
            int sortOrder
    ) {
        return ChecklistItem.builder()
                .sprintId(sprintId)
                .featureId(featureId)
                .title(draft.title())
                .expected(draft.expected())
                .parts(sortParts(draft.parts()))
                .classification(draft.classification())
                .specRowIds(List.copyOf(draft.specRowIds()))
                .sourceRefs(List.copyOf(sourceRefs))
                .origin(ItemOrigin.LLM)
                .sortOrder(sortOrder)
                .build();
    }

    public static ChecklistItem createByHuman(
            long sprintId,
            long featureId,
            String title,
            String expected,
            List<Part> parts,
            Classification classification,
            int sortOrder
    ) {
        return ChecklistItem.builder()
                .sprintId(sprintId)
                .featureId(featureId)
                .title(title)
                .expected(expected)
                .parts(sortParts(parts))
                .classification(classification)
                .specRowIds(List.of())
                .sourceRefs(List.of())
                .origin(ItemOrigin.HUMAN)
                .sortOrder(sortOrder)
                .build();
    }

    public List<Part> getParts() {
        return Part.fromNames(parts);
    }

    public void changeTitle(String title) {
        this.title = title;
    }

    public void changeExpected(String expected) {
        this.expected = expected;
    }

    public void changeParts(List<Part> parts) {
        this.parts = sortParts(parts);
    }

    public void changeClassification(Classification classification) {
        this.classification = classification;
    }

    public void requireVersion(int expected) {
        if (version != expected) {
            throw new RestApiException(VERSION_CONFLICT);
        }
    }

    private static List<String> sortParts(List<Part> parts) {
        return Part.toNames(parts.stream().distinct().sorted().toList());
    }
}
