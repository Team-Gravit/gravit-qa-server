package gravit.qa.sprint.service;

import gravit.qa.job.domain.JobType;
import gravit.qa.sprint.domain.SetupStepKey;
import gravit.qa.sprint.domain.SetupStepStatus;
import gravit.qa.sprint.domain.SprintStep;
import gravit.qa.sprint.dto.internal.SetupStepDto;
import gravit.qa.sprint.repository.SprintStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SetupStepService {

    private final SprintStepRepository sprintStepRepository;

    @Transactional(readOnly = true)
    public List<SetupStepDto> getSteps(long sprintId) {
        Map<SetupStepKey, SprintStep> keyToStep = new EnumMap<>(SetupStepKey.class);
        sprintStepRepository.findAllBySprintId(sprintId).forEach(step -> keyToStep.put(step.getStepKey(), step));

        return Arrays.stream(SetupStepKey.values())
                .map(key -> keyToStep.containsKey(key) ? SetupStepDto.from(keyToStep.get(key)) : SetupStepDto.pending(key))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isDone(
            long sprintId,
            SetupStepKey key
    ) {
        return sprintStepRepository.findBySprintIdAndStepKey(sprintId, key)
                .map(SprintStep::isDone)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public SetupStepStatus getStatus(
            long sprintId,
            SetupStepKey key
    ) {
        return sprintStepRepository.findBySprintIdAndStepKey(sprintId, key)
                .map(SprintStep::getStatus)
                .orElse(SetupStepStatus.PENDING);
    }

    @Transactional
    public void start(
            long sprintId,
            SetupStepKey key,
            LocalDateTime now
    ) {
        findOrCreate(sprintId, key).start(now);
    }

    @Transactional
    public void finish(
            long sprintId,
            SetupStepKey key,
            SetupStepStatus result,
            String message,
            LocalDateTime now
    ) {
        findOrCreate(sprintId, key).finish(result, message, now);
    }

    @Transactional
    public void failRunning(
            long sprintId,
            JobType jobType,
            String message,
            LocalDateTime now
    ) {
        List<SetupStepKey> keys = Arrays.stream(SetupStepKey.values())
                .filter(key -> key.getJobType() == jobType)
                .toList();
        sprintStepRepository.findAllBySprintIdAndStatusAndStepKeyIn(sprintId, SetupStepStatus.RUNNING, keys)
                .forEach(step -> step.finish(SetupStepStatus.FAILED, message, now));
    }

    @Transactional
    public void reset(
            long sprintId,
            SetupStepKey key
    ) {
        findOrCreate(sprintId, key).reset();
    }

    private SprintStep findOrCreate(
            long sprintId,
            SetupStepKey key
    ) {
        return sprintStepRepository.findBySprintIdAndStepKey(sprintId, key)
                .orElseGet(() -> sprintStepRepository.save(SprintStep.create(sprintId, key)));
    }
}
