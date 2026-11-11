package gravit.qa.designReview.domain;

import gravit.qa.checklist.domain.CellSource;
import gravit.qa.checklist.domain.CellState;
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

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "design_review_item_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DesignReviewItemHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private long itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", nullable = false)
    private CellState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false)
    private CellState toState;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private CellSource source;

    @Column(name = "reason")
    private String reason;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private DesignReviewItemHistory(
            long itemId,
            CellState fromState,
            CellState toState,
            CellSource source,
            String reason,
            Long changedBy,
            LocalDateTime changedAt
    ) {
        this.itemId = itemId;
        this.fromState = fromState;
        this.toState = toState;
        this.source = source;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public static DesignReviewItemHistory of(
            long itemId,
            CellState fromState,
            CellState toState,
            CellSource source,
            String reason,
            Long changedBy,
            LocalDateTime changedAt
    ) {
        return DesignReviewItemHistory.builder()
                .itemId(itemId)
                .fromState(fromState)
                .toState(toState)
                .source(source)
                .reason(reason)
                .changedBy(changedBy)
                .changedAt(changedAt)
                .build();
    }
}
