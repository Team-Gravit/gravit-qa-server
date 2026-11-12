package gravit.qa.sprint.domain;

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

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "sprint_step")
@IdClass(SprintStepId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SprintStep {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    @Id
    @Column(name = "sprint_id")
    private Long sprintId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "step_key")
    private SetupStepKey stepKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SetupStepStatus status;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "message")
    private String message;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private SprintStep(
            Long sprintId,
            SetupStepKey stepKey,
            SetupStepStatus status
    ) {
        this.sprintId = sprintId;
        this.stepKey = stepKey;
        this.status = status;
    }

    public static SprintStep create(
            long sprintId,
            SetupStepKey stepKey
    ) {
        return SprintStep.builder()
                .sprintId(sprintId)
                .stepKey(stepKey)
                .status(SetupStepStatus.PENDING)
                .build();
    }

    public boolean isDone() {
        return status == SetupStepStatus.DONE;
    }

    public void start(LocalDateTime now) {
        this.status = SetupStepStatus.RUNNING;
        this.attempt = attempt + 1;
        this.message = null;
        this.startedAt = now;
        this.finishedAt = null;
    }

    public void finish(
            SetupStepStatus result,
            String message,
            LocalDateTime now
    ) {
        this.status = result;
        this.message = truncate(message);
        this.finishedAt = now;
    }

    public void reset() {
        this.status = SetupStepStatus.PENDING;
        this.message = null;
        this.finishedAt = null;
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
