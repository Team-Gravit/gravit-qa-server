package gravit.qa.sprint.repository;

import gravit.qa.sprint.domain.PrepareUnit;
import gravit.qa.sprint.domain.PrepareUnitId;
import gravit.qa.sprint.domain.SetupStepKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrepareUnitRepository extends JpaRepository<PrepareUnit, PrepareUnitId> {

    @Query("""
            SELECT u.unitKey
            FROM PrepareUnit u
            WHERE u.sprintId = :sprintId AND u.stepKey = :stepKey
    """)
    List<String> findUnitKeys(
            @Param("sprintId") long sprintId,
            @Param("stepKey") SetupStepKey stepKey
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM PrepareUnit u
            WHERE u.sprintId = :sprintId AND u.stepKey = :stepKey
    """)
    void deleteAllBySprintIdAndStepKey(
            @Param("sprintId") long sprintId,
            @Param("stepKey") SetupStepKey stepKey
    );
}
