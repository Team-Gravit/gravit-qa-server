package gravit.qa.sprint.service;

import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.sprint.domain.Baseline;
import gravit.qa.sprint.domain.BaselineRepo;
import gravit.qa.sprint.domain.PrepareFailureReason;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.dto.internal.SetupBaselineDto;
import gravit.qa.sprint.dto.internal.SprintDto;
import gravit.qa.sprint.dto.internal.WarningDto;
import gravit.qa.sprint.repository.BaselineRepoRepository;
import gravit.qa.sprint.repository.BaselineRepository;
import gravit.qa.sprint.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_ALREADY_REGISTERED;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class SprintCommandService {

    private final SprintRepository sprintRepository;
    private final BaselineRepository baselineRepository;
    private final BaselineRepoRepository baselineRepoRepository;

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
                notionSprint.periodStart(),
                notionSprint.periodEnd(),
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
    public void startPreparing(long sprintId) {
        findSprint(sprintId).startPreparing();
    }

    @Transactional
    public void markPrepareFailed(
            long sprintId,
            PrepareFailureReason reason
    ) {
        sprintRepository.findById(sprintId).ifPresent(sprint -> sprint.failPreparation(reason));
    }

    @Transactional
    public void applyPreparation(
            long sprintId,
            SetupBaselineDto baseline,
            LocalDate periodStart,
            LocalDate periodEnd,
            String figmaFileKey,
            LocalDateTime now
    ) {
        findSprint(sprintId).completePreparation(periodStart, periodEnd, figmaFileKey, now);

        Baseline saved = baselineRepository.findBySprintId(sprintId)
                .map(existing -> {
                    existing.replace(baseline.previousBaselineId(), now);
                    return existing;
                })
                .orElseGet(() -> baselineRepository.save(Baseline.create(sprintId, baseline.previousBaselineId(), now)));

        long baselineId = saved.getId();
        baselineRepoRepository.deleteAllByBaselineId(baselineId);
        baselineRepoRepository.saveAll(baseline.repos().stream()
                .map(repo -> BaselineRepo.create(baselineId, repo))
                .toList());
    }

    @Transactional
    public void confirm(
            long sprintId,
            int version,
            LocalDateTime now
    ) {
        findSprint(sprintId).confirm(version, now);
    }

    @Transactional
    public void close(
            long sprintId,
            int version,
            LocalDateTime now
    ) {
        findSprint(sprintId).close(version, now);
    }

    @Transactional
    public void addWarning(
            long sprintId,
            WarningDto warning
    ) {
        findSprint(sprintId).addWarning(warning);
    }

    private Sprint findSprint(long sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new RestApiException(SPRINT_NOT_FOUND));
    }
}
