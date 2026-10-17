package gravit.qa.sprint.facade;

import gravit.qa.adapter.exception.ExternalApiException;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.figma.support.FigmaFileKeys;
import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.adapter.notion.infrastructure.NotionClient;
import gravit.qa.checklist.dto.request.ChecklistConfirmRequest;
import gravit.qa.checklist.dto.response.ChecklistConfirmResponse;
import gravit.qa.checklist.dto.response.PartProgressResponse;
import gravit.qa.checklist.service.ChecklistQueryService;
import gravit.qa.feature.domain.Part;
import gravit.qa.global.annotation.Facade;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.global.util.KstTimes;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.job.service.JobQueryService;
import gravit.qa.run.dto.response.AutoRunSummaryResponse;
import gravit.qa.run.service.AutoRunCommandService;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.domain.WarningCode;
import gravit.qa.sprint.dto.internal.SprintDto;
import gravit.qa.sprint.dto.internal.SprintGuideInputDto;
import gravit.qa.sprint.dto.internal.WarningDto;
import gravit.qa.sprint.dto.request.SprintCloseRequest;
import gravit.qa.sprint.dto.request.SprintCreateRequest;
import gravit.qa.sprint.dto.response.SprintGuideResponse;
import gravit.qa.sprint.dto.response.SprintResponse;
import gravit.qa.sprint.dto.response.SprintSummaryResponse;
import gravit.qa.sprint.service.SprintCommandService;
import gravit.qa.sprint.service.SprintQueryService;
import gravit.qa.sprint.support.SprintGuideCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static gravit.qa.global.exception.domain.CustomErrorCode.CHECKLIST_ITEMS_NOT_REVIEWED;
import static gravit.qa.global.exception.domain.CustomErrorCode.NOTION_SPRINT_PAGE_INVALID;
import static gravit.qa.global.exception.domain.CustomErrorCode.NOTION_SPRINT_PAGE_NOT_FOUND;
import static gravit.qa.global.exception.domain.CustomErrorCode.NOTION_UNAVAILABLE;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_AUTO_RUN_IN_PROGRESS;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_FIGMA_URL_INVALID;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_STATUS_CONFLICT;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_UNCHECKED_CELLS_REMAIN;

@Slf4j
@Facade
@RequiredArgsConstructor
public class SprintFacade {

    private static final int STATUS_NOT_FOUND = 404;
    private static final String NOTION_WRITE_FAILED_MESSAGE = "Notion 'QA 기준점' 기록 실패: ";

    private final SprintQueryService sprintQueryService;
    private final SprintCommandService sprintCommandService;
    private final ChecklistQueryService checklistQueryService;
    private final JobQueryService jobQueryService;
    private final JobCommandService jobCommandService;
    private final AutoRunCommandService autoRunCommandService;

    private final NotionClient notionClient;

    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public List<SprintSummaryResponse> listSprints(SprintStatus status) {
        List<SprintDto> sprints = sprintQueryService.getSprints(status);
        List<Long> sprintIds = sprints.stream().map(SprintDto::id).toList();

        Map<Long, List<PartProgressResponse>> sprintIdToProgress = checklistQueryService.getProgress(sprintIds);
        Map<Long, Long> sprintIdToProposedCount = checklistQueryService.countProposed(sprintIds);
        Set<Long> autoRunningSprintIds = jobQueryService.findSprintIdsWithActiveJob(sprintIds, JobType.AUTO_RUN);

        return sprints.stream()
                .map(sprint -> {
                    List<PartProgressResponse> progress = sprintIdToProgress.get(sprint.id());
                    SprintGuideResponse guide = guide(
                            sprint,
                            progress,
                            sprintIdToProposedCount.getOrDefault(sprint.id(), 0L),
                            autoRunningSprintIds.contains(sprint.id())
                    );
                    return SprintSummaryResponse.of(sprint, progress, guide);
                })
                .toList();
    }

    public SprintResponse getSprint(long sprintId) {
        SprintDto sprint = sprintQueryService.getSprint(sprintId);

        List<PartProgressResponse> progress = checklistQueryService.getProgress(List.of(sprintId)).get(sprintId);
        long proposedCount = checklistQueryService.countProposed(sprintId);
        boolean autoRunActive = jobQueryService.hasActiveJob(sprintId, JobType.AUTO_RUN);
        Long latestSetupJobId = jobQueryService.findLatestJobId(sprintId, JobType.SETUP).orElse(null);

        return SprintResponse.of(
                sprint,
                progress,
                guide(sprint, progress, proposedCount, autoRunActive),
                sprintQueryService.findBaseline(sprintId).orElse(null),
                latestSetupJobId
        );
    }

    public SprintResponse createSprint(
            SprintCreateRequest request,
            long adminId
    ) {
        NotionSprintDto notionSprint = fetchNotionSprint(request.notionPageUrl());
        String figmaFileKey = toFigmaFileKey(notionSprint.figmaQaPageUrl());

        SprintDto sprint = sprintCommandService.create(notionSprint, figmaFileKey, adminId);

        return getSprint(sprint.id());
    }

    public SprintResponse closeSprint(
            long sprintId,
            SprintCloseRequest request
    ) {
        SprintDto sprint = sprintQueryService.getSprint(sprintId);
        if (sprint.status() != SprintStatus.IN_PROGRESS) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }

        transactionTemplate.executeWithoutResult(status -> {
            if (checklistQueryService.countUnchecked(sprintId) > 0) {
                throw new RestApiException(SPRINT_UNCHECKED_CELLS_REMAIN);
            }
            if (jobQueryService.hasActiveJob(sprintId, JobType.AUTO_RUN)) {
                throw new RestApiException(SPRINT_AUTO_RUN_IN_PROGRESS);
            }
            sprintCommandService.close(sprintId, request.version(), KstTimes.now(clock));
        });

        writeNotionBaseline(sprint);

        return getSprint(sprintId);
    }

    public ChecklistConfirmResponse confirmChecklist(
            long sprintId,
            ChecklistConfirmRequest request
    ) {
        SprintDto sprint = sprintQueryService.getSprint(sprintId);
        if (sprint.status() != SprintStatus.REVIEW) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }

        AutoRunSummaryResponse autoRun = transactionTemplate.execute(status -> {
            if (checklistQueryService.countProposed(sprintId) > 0) {
                throw new RestApiException(CHECKLIST_ITEMS_NOT_REVIEWED);
            }
            sprintCommandService.confirm(sprintId, request.version(), KstTimes.now(clock));

            if (!request.shouldStartAutoRun() || !checklistQueryService.existsConfirmedAuto(sprintId)) {
                return null;
            }
            long jobId = jobCommandService.enqueue(JobType.AUTO_RUN, sprintId, Map.of());
            return autoRunCommandService.create(sprintId, sprintQueryService.findBaselineId(sprintId), jobId);
        });

        return ChecklistConfirmResponse.of(getSprint(sprintId), autoRun);
    }

    private SprintGuideResponse guide(
            SprintDto sprint,
            List<PartProgressResponse> progress,
            long proposedCount,
            boolean autoRunActive
    ) {
        Map<Part, Long> partToUncheckedCount = new EnumMap<>(Part.class);
        progress.forEach(partProgress -> partToUncheckedCount.put(partProgress.part(), partProgress.unchecked()));

        return SprintGuideCalculator.calculate(SprintGuideInputDto.of(
                sprint.status(),
                sprint.prepareFailureReason(),
                proposedCount,
                partToUncheckedCount,
                autoRunActive
        ));
    }

    private NotionSprintDto fetchNotionSprint(String notionPageUrl) {
        try {
            return notionClient.getSprint(notionPageUrl);
        } catch (NonRetryableExternalApiException e) {
            if (Integer.valueOf(STATUS_NOT_FOUND).equals(e.getStatus())) {
                throw new RestApiException(NOTION_SPRINT_PAGE_NOT_FOUND);
            }
            if (e.getStatus() == null) {
                throw new RestApiException(NOTION_SPRINT_PAGE_INVALID);
            }
            log.warn("Notion 스프린트 조회 실패: {}", e.getMessage());
            throw new RestApiException(NOTION_UNAVAILABLE);
        } catch (ExternalApiException e) {
            log.warn("Notion 스프린트 조회 실패: {}", e.getMessage());
            throw new RestApiException(NOTION_UNAVAILABLE);
        }
    }

    private String toFigmaFileKey(String figmaQaPageUrl) {
        if (!StringUtils.hasText(figmaQaPageUrl)) {
            return null;
        }
        try {
            return FigmaFileKeys.fromUrl(figmaQaPageUrl);
        } catch (NonRetryableExternalApiException e) {
            throw new RestApiException(SPRINT_FIGMA_URL_INVALID);
        }
    }

    private void writeNotionBaseline(SprintDto sprint) {
        try {
            notionClient.updateSprintBaseline(sprint.notionPageId(), sprintQueryService.getNotionBaselineSummary(sprint.id()));
        } catch (RuntimeException e) {
            log.warn("Notion QA 기준점 기록 실패. sprintId={}", sprint.id(), e);
            sprintCommandService.addWarning(sprint.id(), WarningDto.of(WarningCode.NOTION_WRITE_FAILED, NOTION_WRITE_FAILED_MESSAGE + e.getMessage()));
        }
    }
}
