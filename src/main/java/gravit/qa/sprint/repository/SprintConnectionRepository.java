package gravit.qa.sprint.repository;

import gravit.qa.sprint.domain.ConnectionTarget;
import gravit.qa.sprint.domain.SprintConnection;
import gravit.qa.sprint.domain.SprintConnectionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SprintConnectionRepository extends JpaRepository<SprintConnection, SprintConnectionId> {

    List<SprintConnection> findAllBySprintId(long sprintId);

    Optional<SprintConnection> findBySprintIdAndTarget(
            long sprintId,
            ConnectionTarget target
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM SprintConnection c
            WHERE c.sprintId = :sprintId
    """)
    void deleteAllBySprintId(@Param("sprintId") long sprintId);
}
