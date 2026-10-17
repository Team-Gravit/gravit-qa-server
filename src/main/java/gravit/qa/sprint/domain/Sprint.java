package gravit.qa.sprint.domain;

import gravit.qa.global.entity.BaseEntity;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.sprint.dto.internal.WarningDto;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_STATUS_CONFLICT;
import static gravit.qa.global.exception.domain.CustomErrorCode.VERSION_CONFLICT;

@Entity
@Getter
@Table(name = "sprint")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sprint extends BaseEntity {

    private static final Set<SprintStatus> PREPARABLE_STATUSES = EnumSet.of(
            SprintStatus.DRAFT,
            SprintStatus.PREPARE_FAILED,
            SprintStatus.REVIEW
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_key", nullable = false, length = 100)
    private String sprintKey;

    @Column(name = "notion_page_id", nullable = false, length = 64)
    private String notionPageId;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "figma_file_key")
    private String figmaFileKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SprintStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "prepare_failure_reason")
    private PrepareFailureReason prepareFailureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", nullable = false)
    private List<WarningDto> warnings;

    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_by", nullable = false)
    private long createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Builder(access = AccessLevel.PRIVATE)
    private Sprint(
            String sprintKey,
            String notionPageId,
            LocalDate periodStart,
            LocalDate periodEnd,
            String figmaFileKey,
            SprintStatus status,
            List<WarningDto> warnings,
            long createdBy
    ) {
        this.sprintKey = sprintKey;
        this.notionPageId = notionPageId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.figmaFileKey = figmaFileKey;
        this.status = status;
        this.warnings = warnings;
        this.createdBy = createdBy;
    }

    public static Sprint create(
            String sprintKey,
            String notionPageId,
            LocalDate periodStart,
            LocalDate periodEnd,
            String figmaFileKey,
            long createdBy
    ) {
        return Sprint.builder()
                .sprintKey(sprintKey)
                .notionPageId(notionPageId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .figmaFileKey(figmaFileKey)
                .status(SprintStatus.DRAFT)
                .warnings(new ArrayList<>())
                .createdBy(createdBy)
                .build();
    }

    public void startPreparing() {
        if (!PREPARABLE_STATUSES.contains(status)) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }
        this.status = SprintStatus.PREPARING;
        this.prepareFailureReason = null;
    }

    public boolean failPreparation(PrepareFailureReason reason) {
        if (status != SprintStatus.PREPARING) {
            return false;
        }
        this.status = SprintStatus.PREPARE_FAILED;
        this.prepareFailureReason = reason;
        return true;
    }

    public void completePreparation(
            LocalDate periodStart,
            LocalDate periodEnd,
            String figmaFileKey,
            LocalDateTime now
    ) {
        if (status != SprintStatus.PREPARING) {
            throw new IllegalStateException("준비 중인 스프린트가 아니라 결과를 반영할 수 없습니다. sprintId=" + id + ", status=" + status);
        }
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.figmaFileKey = figmaFileKey;
        this.status = SprintStatus.REVIEW;
        this.prepareFailureReason = null;
        this.preparedAt = now;
    }

    public void confirm(
            int version,
            LocalDateTime now
    ) {
        requireStatus(SprintStatus.REVIEW);
        requireVersion(version);
        this.status = SprintStatus.IN_PROGRESS;
        this.confirmedAt = now;
    }

    public void close(
            int version,
            LocalDateTime now
    ) {
        requireStatus(SprintStatus.IN_PROGRESS);
        requireVersion(version);
        this.status = SprintStatus.CLOSED;
        this.closedAt = now;
    }

    public void addWarning(WarningDto warning) {
        List<WarningDto> updated = new ArrayList<>(warnings);
        updated.add(warning);
        this.warnings = updated;
    }

    private void requireStatus(SprintStatus expected) {
        if (status != expected) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }
    }

    private void requireVersion(int expected) {
        if (version != expected) {
            throw new RestApiException(VERSION_CONFLICT);
        }
    }
}
