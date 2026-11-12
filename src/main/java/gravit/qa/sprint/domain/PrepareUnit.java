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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Getter
@Table(name = "prepare_unit")
@IdClass(PrepareUnitId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrepareUnit {

    @Id
    @Column(name = "sprint_id")
    private Long sprintId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "step_key")
    private SetupStepKey stepKey;

    @Id
    @Column(name = "unit_key")
    private String unitKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", nullable = false)
    private Map<String, Object> detail;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PrepareUnit(
            Long sprintId,
            SetupStepKey stepKey,
            String unitKey,
            Map<String, Object> detail,
            LocalDateTime finishedAt
    ) {
        this.sprintId = sprintId;
        this.stepKey = stepKey;
        this.unitKey = unitKey;
        this.detail = detail;
        this.finishedAt = finishedAt;
    }

    public static PrepareUnit finish(
            long sprintId,
            SetupStepKey stepKey,
            String unitKey,
            Map<String, Object> detail,
            LocalDateTime now
    ) {
        return PrepareUnit.builder()
                .sprintId(sprintId)
                .stepKey(stepKey)
                .unitKey(unitKey)
                .detail(new LinkedHashMap<>(detail))
                .finishedAt(now)
                .build();
    }
}
