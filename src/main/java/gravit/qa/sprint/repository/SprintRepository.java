package gravit.qa.sprint.repository;

import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    boolean existsByNotionPageId(String notionPageId);

    boolean existsBySprintKey(String sprintKey);

    List<Sprint> findAllByOrderByCreatedAtDescIdDesc();

    List<Sprint> findAllByStatusOrderByCreatedAtDescIdDesc(SprintStatus status);
}
