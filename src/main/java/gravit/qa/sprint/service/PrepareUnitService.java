package gravit.qa.sprint.service;

import gravit.qa.sprint.domain.PrepareUnit;
import gravit.qa.sprint.domain.SetupStepKey;
import gravit.qa.sprint.repository.PrepareUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PrepareUnitService {

    private final PrepareUnitRepository prepareUnitRepository;

    @Transactional(readOnly = true)
    public Set<String> findDoneUnitKeys(
            long sprintId,
            SetupStepKey key
    ) {
        return Set.copyOf(prepareUnitRepository.findUnitKeys(sprintId, key));
    }

    @Transactional
    public void finish(
            long sprintId,
            SetupStepKey key,
            String unitKey,
            Map<String, Object> detail,
            LocalDateTime now
    ) {
        prepareUnitRepository.save(PrepareUnit.finish(sprintId, key, unitKey, detail, now));
    }

    @Transactional
    public void deleteAll(
            long sprintId,
            SetupStepKey key
    ) {
        prepareUnitRepository.deleteAllBySprintIdAndStepKey(sprintId, key);
    }
}
