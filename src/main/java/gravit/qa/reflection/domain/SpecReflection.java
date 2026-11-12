package gravit.qa.reflection.domain;

import gravit.qa.global.entity.BaseEntity;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.reflection.dto.internal.ReflectionHistoryRowDto;
import gravit.qa.reflection.dto.internal.ReflectionOperationDto;
import gravit.qa.reflection.dto.internal.ReflectionProposalDto;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_STATUS_CONFLICT;
import static gravit.qa.global.exception.domain.CustomErrorCode.VERSION_CONFLICT;

@Entity
@Getter
@Table(name = "spec_reflection")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpecReflection extends BaseEntity {

    private static final Set<ReflectionState> APPROVABLE_STATES = EnumSet.of(ReflectionState.PENDING, ReflectionState.FAILED);
    private static final Set<ReflectionState> REJECTABLE_STATES = EnumSet.of(ReflectionState.PENDING, ReflectionState.FAILED, ReflectionState.APPROVED);
    private static final int MAX_MESSAGE_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_id", nullable = false)
    private long sprintId;

    @Column(name = "feature_id", nullable = false)
    private long featureId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "history_rows", nullable = false)
    private List<ReflectionHistoryRowDto> historyRows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operations", nullable = false)
    private List<ReflectionOperationDto> operations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "base_blocks", nullable = false)
    private Map<String, String> baseBlocks;

    @Column(name = "before_markdown", nullable = false)
    private String beforeMarkdown;

    @Column(name = "after_markdown", nullable = false)
    private String afterMarkdown;

    @Column(name = "appliable", nullable = false)
    private boolean appliable;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ReflectionState state;

    @Column(name = "message")
    private String message;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Builder(access = AccessLevel.PRIVATE)
    private SpecReflection(
            long sprintId,
            long featureId,
            List<ReflectionHistoryRowDto> historyRows,
            List<ReflectionOperationDto> operations,
            Map<String, String> baseBlocks,
            String beforeMarkdown,
            String afterMarkdown,
            boolean appliable,
            ReflectionState state,
            String message
    ) {
        this.sprintId = sprintId;
        this.featureId = featureId;
        this.historyRows = historyRows;
        this.operations = operations;
        this.baseBlocks = baseBlocks;
        this.beforeMarkdown = beforeMarkdown;
        this.afterMarkdown = afterMarkdown;
        this.appliable = appliable;
        this.state = state;
        this.message = message;
    }

    public static SpecReflection create(
            long sprintId,
            long featureId,
            ReflectionProposalDto proposal
    ) {
        return SpecReflection.builder()
                .sprintId(sprintId)
                .featureId(featureId)
                .historyRows(List.copyOf(proposal.historyRows()))
                .operations(List.copyOf(proposal.operations()))
                .baseBlocks(Map.copyOf(proposal.baseBlocks()))
                .beforeMarkdown(proposal.beforeMarkdown())
                .afterMarkdown(proposal.afterMarkdown())
                .appliable(proposal.appliable())
                .state(proposal.appliable() ? ReflectionState.PENDING : ReflectionState.FAILED)
                .message(truncate(proposal.message()))
                .build();
    }

    public void startApply(int version) {
        requireVersion(version);
        if (!appliable || !APPROVABLE_STATES.contains(state)) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }
        this.state = ReflectionState.APPROVED;
        this.message = null;
    }

    public void markOperationApplied(
            int index,
            String createdBlockId
    ) {
        List<ReflectionOperationDto> updated = new ArrayList<>(operations);
        updated.set(index, updated.get(index).markApplied(createdBlockId));
        this.operations = updated;
    }

    public void complete(
            long adminId,
            LocalDateTime now
    ) {
        this.state = ReflectionState.APPLIED;
        this.message = null;
        this.decidedBy = adminId;
        this.decidedAt = now;
    }

    public void fail(
            String message,
            long adminId,
            LocalDateTime now
    ) {
        this.state = ReflectionState.FAILED;
        this.message = truncate(message);
        this.decidedBy = adminId;
        this.decidedAt = now;
    }

    public void reject(
            int version,
            long adminId,
            LocalDateTime now
    ) {
        requireVersion(version);
        if (!REJECTABLE_STATES.contains(state)) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }
        this.state = ReflectionState.REJECTED;
        this.message = null;
        this.decidedBy = adminId;
        this.decidedAt = now;
    }

    public boolean hasAppliedOperation() {
        return operations.stream().anyMatch(ReflectionOperationDto::applied);
    }

    private void requireVersion(int expected) {
        if (version != expected) {
            throw new RestApiException(VERSION_CONFLICT);
        }
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
