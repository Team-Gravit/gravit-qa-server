package gravit.qa.run.domain;

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

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "auto_run")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutoRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_id", nullable = false)
    private long sprintId;

    @Column(name = "baseline_id", nullable = false)
    private long baselineId;

    @Column(name = "job_id", nullable = false)
    private long jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RunStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fixture_manifest")
    private String fixtureManifest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counts")
    private String counts;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private AutoRun(
            long sprintId,
            long baselineId,
            long jobId,
            RunStatus status,
            LocalDateTime createdAt
    ) {
        this.sprintId = sprintId;
        this.baselineId = baselineId;
        this.jobId = jobId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static AutoRun create(
            long sprintId,
            long baselineId,
            long jobId,
            LocalDateTime now
    ) {
        return AutoRun.builder()
                .sprintId(sprintId)
                .baselineId(baselineId)
                .jobId(jobId)
                .status(RunStatus.QUEUED)
                .createdAt(now)
                .build();
    }
}
