package gravit.qa.sprint.domain;

import gravit.qa.sprint.dto.internal.ConnectionResultDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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
@Table(name = "sprint_connection")
@IdClass(SprintConnectionId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SprintConnection {

    @Id
    @Column(name = "sprint_id")
    private Long sprintId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "target")
    private ConnectionTarget target;

    @Column(name = "connected", nullable = false)
    private boolean connected;

    @Column(name = "branch")
    private String branch;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "commit_sha", length = 40)
    private String commitSha;

    @Column(name = "message")
    private String message;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private SprintConnection(
            Long sprintId,
            ConnectionTarget target,
            boolean connected,
            String branch,
            String commitSha,
            String message,
            LocalDateTime checkedAt
    ) {
        this.sprintId = sprintId;
        this.target = target;
        this.connected = connected;
        this.branch = branch;
        this.commitSha = commitSha;
        this.message = message;
        this.checkedAt = checkedAt;
    }

    public static SprintConnection create(
            long sprintId,
            ConnectionResultDto result,
            LocalDateTime now
    ) {
        return SprintConnection.builder()
                .sprintId(sprintId)
                .target(result.target())
                .connected(result.connected())
                .branch(result.branch())
                .commitSha(result.commitSha())
                .message(result.message())
                .checkedAt(now)
                .build();
    }
}
