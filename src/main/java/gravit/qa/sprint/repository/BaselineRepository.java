package gravit.qa.sprint.repository;

import gravit.qa.sprint.domain.Baseline;
import gravit.qa.sprint.domain.SprintStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BaselineRepository extends JpaRepository<Baseline, Long> {

    Optional<Baseline> findBySprintId(long sprintId);

    @Query("""
            SELECT b
            FROM Baseline b JOIN Sprint s ON s.id = b.sprintId
            WHERE s.status = :closed AND s.id <> :sprintId
            ORDER BY s.closedAt DESC, s.id DESC
    """)
    List<Baseline> findClosedBefore(
            @Param("sprintId") long sprintId,
            @Param("closed") SprintStatus closed,
            Limit limit
    );
}
