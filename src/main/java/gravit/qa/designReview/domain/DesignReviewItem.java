package gravit.qa.designReview.domain;

import gravit.qa.checklist.domain.CellSource;
import gravit.qa.checklist.domain.CellState;
import gravit.qa.designReview.dto.internal.DesignReviewItemDraftDto;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static gravit.qa.global.exception.domain.CustomErrorCode.VERSION_CONFLICT;

@Entity
@Getter
@Table(name = "design_review_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DesignReviewItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_id", nullable = false)
    private long sprintId;

    @Column(name = "feature_id", nullable = false)
    private long featureId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private DesignPlatform platform;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "expected", nullable = false)
    private String expected;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "spec_row_ids", nullable = false)
    private List<String> specRowIds;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "figma_node_ids", nullable = false)
    private List<String> figmaNodeIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private CellState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private CellSource source;

    @Column(name = "reason")
    private String reason;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Builder(access = AccessLevel.PRIVATE)
    private DesignReviewItem(
            long sprintId,
            long featureId,
            DesignPlatform platform,
            String title,
            String expected,
            List<String> specRowIds,
            List<String> figmaNodeIds,
            CellState state,
            CellSource source,
            String reason,
            int sortOrder
    ) {
        this.sprintId = sprintId;
        this.featureId = featureId;
        this.platform = platform;
        this.title = title;
        this.expected = expected;
        this.specRowIds = specRowIds;
        this.figmaNodeIds = figmaNodeIds;
        this.state = state;
        this.source = source;
        this.reason = reason;
        this.sortOrder = sortOrder;
    }

    public static DesignReviewItem judge(
            long sprintId,
            long featureId,
            DesignReviewItemDraftDto draft,
            int sortOrder
    ) {
        return DesignReviewItem.builder()
                .sprintId(sprintId)
                .featureId(featureId)
                .platform(draft.platform())
                .title(draft.title())
                .expected(draft.expected())
                .specRowIds(List.copyOf(draft.specRowIds()))
                .figmaNodeIds(List.copyOf(draft.figmaNodeIds()))
                .state(draft.state())
                .source(CellSource.AUTO)
                .reason(draft.reason())
                .sortOrder(sortOrder)
                .build();
    }

    public DesignReviewItemHistory change(
            CellState target,
            long adminId,
            LocalDateTime now
    ) {
        DesignReviewItemHistory history = DesignReviewItemHistory.of(id, state, target, CellSource.MANUAL, null, adminId, now);
        this.state = target;
        this.source = CellSource.MANUAL;
        this.reason = null;
        this.updatedBy = adminId;
        return history;
    }

    public void changeTitle(String title) {
        this.title = title;
    }

    public void changeExpected(String expected) {
        this.expected = expected;
    }

    public Optional<DesignReviewItemHistory> resetJudgment(
            String reason,
            long adminId,
            LocalDateTime now
    ) {
        if (state == CellState.UNCHECKED && source == CellSource.MANUAL) {
            return Optional.empty();
        }
        DesignReviewItemHistory history = DesignReviewItemHistory.of(id, state, CellState.UNCHECKED, CellSource.MANUAL, reason, adminId, now);
        this.state = CellState.UNCHECKED;
        this.source = CellSource.MANUAL;
        this.reason = reason;
        this.updatedBy = adminId;
        return Optional.of(history);
    }

    public DesignReviewItemHistory initialHistory(LocalDateTime now) {
        return DesignReviewItemHistory.of(id, CellState.UNCHECKED, state, CellSource.AUTO, reason, null, now);
    }

    public void requireVersion(int expected) {
        if (version != expected) {
            throw new RestApiException(VERSION_CONFLICT);
        }
    }
}
