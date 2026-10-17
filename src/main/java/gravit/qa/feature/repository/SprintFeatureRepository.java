package gravit.qa.feature.repository;

import gravit.qa.feature.domain.SprintFeature;
import gravit.qa.feature.domain.SprintFeatureId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SprintFeatureRepository extends JpaRepository<SprintFeature, SprintFeatureId> {

    List<SprintFeature> findAllBySprintId(long sprintId);

    boolean existsBySprintIdAndFeatureId(
            long sprintId,
            long featureId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM SprintFeature f
            WHERE f.sprintId = :sprintId
    """)
    void deleteAllBySprintId(@Param("sprintId") long sprintId);
}
