package gravit.qa.sprint.service;

import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.sprint.domain.ConnectionTarget;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.dto.internal.ConnectionResultDto;
import gravit.qa.sprint.dto.internal.SetupTargetDto;
import gravit.qa.sprint.dto.internal.SprintDto;
import gravit.qa.sprint.repository.SprintConnectionRepository;
import gravit.qa.sprint.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class SprintQueryService {

    private final SprintRepository sprintRepository;
    private final SprintConnectionRepository sprintConnectionRepository;

    @Transactional(readOnly = true)
    public SprintDto getSprint(long sprintId) {
        return SprintDto.from(findSprint(sprintId));
    }

    @Transactional(readOnly = true)
    public List<SprintDto> getSprints() {
        return sprintRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(SprintDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SetupTargetDto getSetupTarget(long sprintId) {
        return SetupTargetDto.from(findSprint(sprintId));
    }

    @Transactional(readOnly = true)
    public List<ConnectionResultDto> getConnections(long sprintId) {
        return sprintConnectionRepository.findAllBySprintId(sprintId).stream()
                .map(ConnectionResultDto::from)
                .sorted(Comparator.comparing(ConnectionResultDto::target))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ConnectionResultDto> findConnection(
            long sprintId,
            ConnectionTarget target
    ) {
        return sprintConnectionRepository.findBySprintIdAndTarget(sprintId, target)
                .map(ConnectionResultDto::from);
    }

    private Sprint findSprint(long sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new RestApiException(SPRINT_NOT_FOUND));
    }
}
