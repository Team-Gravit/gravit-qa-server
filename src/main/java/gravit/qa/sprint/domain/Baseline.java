package gravit.qa.sprint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "baseline")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Baseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_id", nullable = false)
    private long sprintId;

    @Column(name = "previous_baseline_id")
    private Long previousBaselineId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Baseline(
            long sprintId,
            Long previousBaselineId,
            LocalDateTime createdAt
    ) {
        this.sprintId = sprintId;
        this.previousBaselineId = previousBaselineId;
        this.createdAt = createdAt;
    }

    public static Baseline create(
            long sprintId,
            Long previousBaselineId,
            LocalDateTime now
    ) {
        return Baseline.builder()
                .sprintId(sprintId)
                .previousBaselineId(previousBaselineId)
                .createdAt(now)
                .build();
    }

    public void replace(
            Long previousBaselineId,
            LocalDateTime now
    ) {
        this.previousBaselineId = previousBaselineId;
        this.createdAt = now;
    }
}
