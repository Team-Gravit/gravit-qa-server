package gravit.qa.checklist.domain;

import gravit.qa.feature.domain.Part;
import gravit.qa.global.exception.domain.RestApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static gravit.qa.global.exception.domain.CustomErrorCode.VERSION_CONFLICT;

@Entity
@Getter
@Table(name = "check_cell")
@IdClass(CheckCellId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckCell {

    @Id
    @Column(name = "item_id")
    private Long itemId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "part")
    private Part part;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private CellState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private CellSource source;

    @Column(name = "auto_run_id")
    private Long autoRunId;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Builder(access = AccessLevel.PRIVATE)
    private CheckCell(
            Long itemId,
            Part part,
            CellState state,
            CellSource source
    ) {
        this.itemId = itemId;
        this.part = part;
        this.state = state;
        this.source = source;
    }

    public static List<CheckCell> initialize(
            long itemId,
            Collection<Part> itemParts
    ) {
        return Arrays.stream(Part.values())
                .map(part -> CheckCell.builder()
                        .itemId(itemId)
                        .part(part)
                        .state(itemParts.contains(part) ? CellState.UNCHECKED : CellState.NOT_APPLICABLE)
                        .source(CellSource.MANUAL)
                        .build())
                .toList();
    }

    public Optional<CheckCellHistory> applyParts(
            Collection<Part> itemParts,
            long adminId,
            LocalDateTime now
    ) {
        CellState target = itemParts.contains(part) ? CellState.UNCHECKED : CellState.NOT_APPLICABLE;
        boolean included = target == CellState.UNCHECKED;
        if (included == (state != CellState.NOT_APPLICABLE)) {
            return Optional.empty();
        }
        return Optional.of(change(target, adminId, now));
    }

    public CheckCellHistory check(
            CellState target,
            long adminId,
            LocalDateTime now
    ) {
        return change(target, adminId, now);
    }

    public void requireVersion(int expected) {
        if (version == null || version != expected) {
            throw new RestApiException(VERSION_CONFLICT);
        }
    }

    private CheckCellHistory change(
            CellState target,
            long adminId,
            LocalDateTime now
    ) {
        CheckCellHistory history = CheckCellHistory.of(itemId, part, state, target, CellSource.MANUAL, adminId, null, now);
        this.state = target;
        this.source = CellSource.MANUAL;
        this.autoRunId = null;
        this.updatedBy = adminId;
        this.updatedAt = now;
        return history;
    }
}
