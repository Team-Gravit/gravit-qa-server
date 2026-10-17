package gravit.qa.run.repository;

import gravit.qa.run.domain.AutoRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoRunRepository extends JpaRepository<AutoRun, Long> {
}
