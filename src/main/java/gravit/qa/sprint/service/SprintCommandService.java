package gravit.qa.sprint.service;

import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintConnection;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.ConnectionResultDto;
import gravit.qa.sprint.dto.internal.SprintDto;
import gravit.qa.sprint.repository.SprintConnectionRepository;
import gravit.qa.sprint.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_ALREADY_REGISTERED;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class SprintCommandService {

    private final SprintRepository sprintRepository;
    private final SprintConnectionRepository sprintConnectionRepository;

    @Transactional
    public SprintDto create(
            NotionSprintDto notionSprint,
            String figmaFileKey,
            long adminId
    ) {
        if (sprintRepository.existsByNotionPageId(notionSprint.pageId()) || sprintRepository.existsBySprintKey(notionSprint.name())) {
            throw new RestApiException(SPRINT_ALREADY_REGISTERED);
        }

        Sprint sprint = Sprint.create(
                notionSprint.name(),
                notionSprint.pageId(),
                notionSprint.changeSummary(),
                figmaFileKey,
                adminId
        );
        try {
            return SprintDto.from(sprintRepository.saveAndFlush(sprint));
        } catch (DataIntegrityViolationException e) {
            throw new RestApiException(SPRINT_ALREADY_REGISTERED);
        }
    }

    @Transactional
    public void requireVersion(
            long sprintId,
            int version
    ) {
        findSprint(sprintId).requireVersion(version);
    }

    @Transactional
    public void startSpecLoad(
            long sprintId,
            int version
    ) {
        Sprint sprint = findSprint(sprintId);
        sprint.requireVersion(version);
        sprint.startSpecLoad();
    }

    @Transactional
    public void applyConnect(
            long sprintId,
            String changeSummary,
            String figmaFileKey,
            List<ConnectionResultDto> connections,
            boolean notionConnected,
            LocalDateTime now
    ) {
        Sprint sprint = findSprint(sprintId);
        if (notionConnected) {
            sprint.applyNotionSprint(changeSummary, figmaFileKey);
        }

        sprintConnectionRepository.deleteAllBySprintId(sprintId);
        sprintConnectionRepository.saveAll(connections.stream()
                .map(connection -> SprintConnection.create(sprintId, connection, now))
                .toList());
    }

    @Transactional
    public void advanceFrom(
            long sprintId,
            SprintStatus current
    ) {
        findSprint(sprintId).advanceFrom(current);
    }

    @Transactional
    public void close(
            long sprintId,
            int version,
            LocalDateTime now
    ) {
        findSprint(sprintId).close(version, now);
    }

    private Sprint findSprint(long sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new RestApiException(SPRINT_NOT_FOUND));
    }
}
