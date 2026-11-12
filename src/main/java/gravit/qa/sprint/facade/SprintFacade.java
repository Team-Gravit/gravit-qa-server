package gravit.qa.sprint.facade;

import gravit.qa.adapter.exception.ExternalApiException;
import gravit.qa.adapter.exception.NonRetryableExternalApiException;
import gravit.qa.adapter.figma.support.FigmaFileKeys;
import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.adapter.notion.infrastructure.NotionClient;
import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.service.ChecklistQueryService;
import gravit.qa.checklist.support.ProgressCalculator;
import gravit.qa.designReview.service.DesignReviewQueryService;
import gravit.qa.feature.domain.Part;
import gravit.qa.global.annotation.Facade;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.global.util.KstTimes;
import gravit.qa.job.service.JobQueryService;
import gravit.qa.reflection.service.ReflectionQueryService;
import gravit.qa.sprint.domain.ProgressPart;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.SprintDto;
import gravit.qa.sprint.dto.request.SprintCloseRequest;
import gravit.qa.sprint.dto.request.SprintCreateRequest;
import gravit.qa.sprint.dto.response.ConnectionResponse;
import gravit.qa.sprint.dto.response.PartProgressResponse;
import gravit.qa.sprint.dto.response.SetupStepResponse;
import gravit.qa.sprint.dto.response.SprintResponse;
import gravit.qa.sprint.dto.response.SprintSummaryResponse;
import gravit.qa.sprint.service.SetupStepService;
import gravit.qa.sprint.service.SprintCommandService;
import gravit.qa.sprint.service.SprintQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static gravit.qa.global.exception.domain.CustomErrorCode.JOB_ALREADY_ACTIVE;
import static gravit.qa.global.exception.domain.CustomErrorCode.NOTION_SPRINT_PAGE_INVALID;
import static gravit.qa.global.exception.domain.CustomErrorCode.NOTION_SPRINT_PAGE_NOT_FOUND;
import static gravit.qa.global.exception.domain.CustomErrorCode.NOTION_UNAVAILABLE;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_FIGMA_URL_INVALID;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_STATUS_CONFLICT;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_UNCHECKED_CELLS_REMAIN;

@Slf4j
@Facade
@RequiredArgsConstructor
public class SprintFacade {

    private static final int STATUS_NOT_FOUND = 404;

    private final SprintQueryService sprintQueryService;
    private final SprintCommandService sprintCommandService;
    private final SetupStepService setupStepService;
    private final ChecklistQueryService checklistQueryService;
    private final DesignReviewQueryService designReviewQueryService;
    private final ReflectionQueryService reflectionQueryService;
    private final JobQueryService jobQueryService;

    private final NotionClient notionClient;

    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public List<SprintSummaryResponse> listSprints() {
        return sprintQueryService.getSprints().stream()
                .map(SprintSummaryResponse::from)
                .toList();
    }

    public SprintResponse getSprint(long sprintId) {
        SprintDto sprint = sprintQueryService.getSprint(sprintId);

        List<ConnectionResponse> connections = sprintQueryService.getConnections(sprintId).stream()
                .map(ConnectionResponse::from)
                .toList();
        List<SetupStepResponse> setupSteps = setupStepService.getSteps(sprintId).stream()
                .map(SetupStepResponse::from)
                .toList();

        return SprintResponse.of(
                sprint,
                connections,
                progress(sprintId),
                setupSteps,
                jobQueryService.findLatestJobResponse(sprintId).orElse(null),
                reflectionQueryService.countUnresolved(sprintId)
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
        if (sprintQueryService.getSprint(sprintId).status() != SprintStatus.MANUAL_REVIEW) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }
        if (jobQueryService.hasActiveJob(sprintId)) {
            throw new RestApiException(JOB_ALREADY_ACTIVE);
        }

        transactionTemplate.executeWithoutResult(status -> {
            sprintCommandService.requireVersion(sprintId, request.version());
            if (checklistQueryService.countUnchecked(sprintId) > 0 || designReviewQueryService.countUnchecked(sprintId) > 0) {
                throw new RestApiException(SPRINT_UNCHECKED_CELLS_REMAIN);
            }
            sprintCommandService.close(sprintId, request.version(), KstTimes.now(clock));
        });

        return getSprint(sprintId);
    }

    private List<PartProgressResponse> progress(long sprintId) {
        Map<Part, Map<CellState, Long>> partToCounts = checklistQueryService.countStates(sprintId);
        Map<CellState, Long> designCounts = designReviewQueryService.countStates(sprintId);
        return List.of(
                PartProgressResponse.of(ProgressPart.DESIGN, ProgressCalculator.count(designCounts)),
                PartProgressResponse.of(ProgressPart.WEB, ProgressCalculator.count(partToCounts.getOrDefault(Part.WEB, Map.of()))),
                PartProgressResponse.of(ProgressPart.APP, ProgressCalculator.count(partToCounts.getOrDefault(Part.APP, Map.of()))),
                PartProgressResponse.of(ProgressPart.SERVER, ProgressCalculator.count(partToCounts.getOrDefault(Part.SERVER, Map.of())))
        );
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
}
