package gravit.qa.sprint.repository;

import gravit.qa.sprint.domain.SetupStepKey;
import gravit.qa.sprint.domain.SetupStepStatus;
import gravit.qa.sprint.domain.SprintStep;
import gravit.qa.sprint.domain.SprintStepId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SprintStepRepository extends JpaRepository<SprintStep, SprintStepId> {

    List<SprintStep> findAllBySprintId(long sprintId);

    Optional<SprintStep> findBySprintIdAndStepKey(
            long sprintId,
            SetupStepKey stepKey
    );

    List<SprintStep> findAllBySprintIdAndStatusAndStepKeyIn(
            long sprintId,
            SetupStepStatus status,
            Collection<SetupStepKey> stepKeys
    );
}
