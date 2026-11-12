package gravit.qa.reflection.repository;

import gravit.qa.reflection.domain.ReflectionState;
import gravit.qa.reflection.domain.SpecReflection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SpecReflectionRepository extends JpaRepository<SpecReflection, Long> {

    List<SpecReflection> findAllBySprintIdOrderByIdAsc(long sprintId);

    long countBySprintIdAndStateIn(
            long sprintId,
            Collection<ReflectionState> states
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM SpecReflection r
            WHERE r.sprintId = :sprintId AND r.state <> :kept
    """)
    void deleteAllExcept(
            @Param("sprintId") long sprintId,
            @Param("kept") ReflectionState kept
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM SpecReflection r
            WHERE r.sprintId = :sprintId AND r.featureId = :featureId AND r.state <> :kept
    """)
    void deleteAllOfFeatureExcept(
            @Param("sprintId") long sprintId,
            @Param("featureId") long featureId,
            @Param("kept") ReflectionState kept
    );
}
