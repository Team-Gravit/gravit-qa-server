package gravit.qa.snapshot.repository;

import gravit.qa.snapshot.domain.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {
}
