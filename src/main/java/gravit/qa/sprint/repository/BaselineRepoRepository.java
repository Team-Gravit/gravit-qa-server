package gravit.qa.sprint.repository;

import gravit.qa.sprint.domain.BaselineRepo;
import gravit.qa.sprint.domain.BaselineRepoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BaselineRepoRepository extends JpaRepository<BaselineRepo, BaselineRepoId> {

    List<BaselineRepo> findAllByBaselineIdOrderByRepo(long baselineId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM BaselineRepo r
            WHERE r.baselineId = :baselineId
    """)
    void deleteAllByBaselineId(@Param("baselineId") long baselineId);
}
