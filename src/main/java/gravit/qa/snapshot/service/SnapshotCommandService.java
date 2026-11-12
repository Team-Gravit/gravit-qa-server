package gravit.qa.snapshot.service;

import gravit.qa.snapshot.domain.Snapshot;
import gravit.qa.snapshot.dto.internal.SnapshotCreateDto;
import gravit.qa.snapshot.repository.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SnapshotCommandService {

    private final SnapshotRepository snapshotRepository;

    @Transactional
    public List<Long> saveAll(
            long sprintId,
            long jobId,
            List<SnapshotCreateDto> snapshots
    ) {
        return snapshotRepository.saveAll(snapshots.stream()
                        .map(snapshot -> Snapshot.create(sprintId, jobId, snapshot))
                        .toList())
                .stream()
                .map(Snapshot::getId)
                .toList();
    }
}
