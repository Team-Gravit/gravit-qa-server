package gravit.qa.checklist.domain;

import gravit.qa.feature.domain.Part;
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
@Table(name = "check_cell_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckCellHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private long itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "part", nullable = false)
    private Part part;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", nullable = false)
    private CellState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false)
    private CellState toState;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private CellSource source;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "auto_run_id")
    private Long autoRunId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private CheckCellHistory(
            long itemId,
            Part part,
            CellState fromState,
            CellState toState,
            CellSource source,
            Long changedBy,
            Long autoRunId,
            LocalDateTime changedAt
    ) {
        this.itemId = itemId;
        this.part = part;
        this.fromState = fromState;
        this.toState = toState;
        this.source = source;
        this.changedBy = changedBy;
        this.autoRunId = autoRunId;
        this.changedAt = changedAt;
    }

    public static CheckCellHistory of(
            long itemId,
            Part part,
            CellState fromState,
            CellState toState,
            CellSource source,
            Long changedBy,
            Long autoRunId,
            LocalDateTime changedAt
    ) {
        return CheckCellHistory.builder()
                .itemId(itemId)
                .part(part)
                .fromState(fromState)
                .toState(toState)
                .source(source)
                .changedBy(changedBy)
                .autoRunId(autoRunId)
                .changedAt(changedAt)
                .build();
    }
}
