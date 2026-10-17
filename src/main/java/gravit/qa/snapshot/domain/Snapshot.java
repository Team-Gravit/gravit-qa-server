package gravit.qa.snapshot.domain;

import gravit.qa.snapshot.dto.internal.SnapshotCreateDto;
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
@Table(name = "snapshot")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Snapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_id", nullable = false)
    private long sprintId;

    @Column(name = "job_id")
    private Long jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private SnapshotSource source;

    @Column(name = "source_ref", nullable = false, length = 512)
    private String sourceRef;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Snapshot(
            long sprintId,
            Long jobId,
            SnapshotSource source,
            String sourceRef,
            String contentHash,
            String storagePath,
            LocalDateTime capturedAt
    ) {
        this.sprintId = sprintId;
        this.jobId = jobId;
        this.source = source;
        this.sourceRef = sourceRef;
        this.contentHash = contentHash;
        this.storagePath = storagePath;
        this.capturedAt = capturedAt;
    }

    public static Snapshot create(
            long sprintId,
            long jobId,
            SnapshotCreateDto snapshot
    ) {
        return Snapshot.builder()
                .sprintId(sprintId)
                .jobId(jobId)
                .source(snapshot.source())
                .sourceRef(snapshot.sourceRef())
                .contentHash(snapshot.contentHash())
                .storagePath(snapshot.storagePath())
                .capturedAt(snapshot.capturedAt())
                .build();
    }
}
