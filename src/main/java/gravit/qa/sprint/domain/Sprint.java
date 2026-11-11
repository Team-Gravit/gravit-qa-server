package gravit.qa.sprint.domain;

import gravit.qa.global.entity.BaseEntity;
import gravit.qa.global.exception.domain.RestApiException;
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

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_STATUS_CONFLICT;
import static gravit.qa.global.exception.domain.CustomErrorCode.VERSION_CONFLICT;

@Entity
@Getter
@Table(name = "sprint")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sprint extends BaseEntity {

    private static final Map<SprintStatus, SprintStatus> NEXT_PREPARE_STATUS = new EnumMap<>(Map.of(
            SprintStatus.SPEC_LOAD, SprintStatus.CHECKLIST,
            SprintStatus.CHECKLIST, SprintStatus.DESIGN_REVIEW,
            SprintStatus.DESIGN_REVIEW, SprintStatus.AUTO_REVIEW,
            SprintStatus.AUTO_REVIEW, SprintStatus.MANUAL_REVIEW
    ));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_key", nullable = false, length = 100)
    private String sprintKey;

    @Column(name = "notion_page_id", nullable = false, length = 64)
    private String notionPageId;

    @Column(name = "change_summary")
    private String changeSummary;

    @Column(name = "figma_file_key")
    private String figmaFileKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SprintStatus status;

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
            String changeSummary,
            String figmaFileKey,
            SprintStatus status,
            long createdBy
    ) {
        this.sprintKey = sprintKey;
        this.notionPageId = notionPageId;
        this.changeSummary = changeSummary;
        this.figmaFileKey = figmaFileKey;
        this.status = status;
        this.createdBy = createdBy;
    }

    public static Sprint create(
            String sprintKey,
            String notionPageId,
            String changeSummary,
            String figmaFileKey,
            long createdBy
    ) {
        return Sprint.builder()
                .sprintKey(sprintKey)
                .notionPageId(notionPageId)
                .changeSummary(changeSummary)
                .figmaFileKey(figmaFileKey)
                .status(SprintStatus.DRAFT)
                .createdBy(createdBy)
                .build();
    }

    public void startSpecLoad() {
        requireStatus(SprintStatus.DRAFT);
        this.status = SprintStatus.SPEC_LOAD;
    }

    public void advanceFrom(SprintStatus current) {
        SprintStatus next = NEXT_PREPARE_STATUS.get(current);
        if (next == null || status != current) {
            throw new IllegalStateException("스프린트를 다음 단계로 넘길 수 없습니다. sprintId=" + id + ", status=" + status + ", expected=" + current);
        }
        this.status = next;
    }

    public void applyNotionSprint(
            String changeSummary,
            String figmaFileKey
    ) {
        this.changeSummary = changeSummary;
        this.figmaFileKey = figmaFileKey;
    }

    public void close(
            int version,
            LocalDateTime now
    ) {
        requireStatus(SprintStatus.MANUAL_REVIEW);
        requireVersion(version);
        this.status = SprintStatus.CLOSED;
        this.closedAt = now;
    }

    public void requireVersion(int expected) {
        if (version != expected) {
            throw new RestApiException(VERSION_CONFLICT);
        }
    }

    private void requireStatus(SprintStatus expected) {
        if (status != expected) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }
    }
}
