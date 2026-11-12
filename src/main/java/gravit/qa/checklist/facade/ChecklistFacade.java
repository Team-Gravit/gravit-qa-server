package gravit.qa.checklist.facade;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.request.CheckCellUpdateRequest;
import gravit.qa.checklist.dto.request.ChecklistItemCreateRequest;
import gravit.qa.checklist.dto.request.ChecklistItemUpdateRequest;
import gravit.qa.checklist.dto.response.CheckCellResponse;
import gravit.qa.checklist.dto.response.ChecklistFeatureResponse;
import gravit.qa.checklist.dto.response.ChecklistItemResponse;
import gravit.qa.checklist.dto.response.ChecklistResponse;
import gravit.qa.checklist.service.ChecklistCommandService;
import gravit.qa.checklist.service.ChecklistQueryService;
import gravit.qa.checklist.support.ProgressCalculator;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.dto.internal.FeatureDto;
import gravit.qa.feature.dto.internal.SprintFeatureDto;
import gravit.qa.feature.service.FeatureQueryService;
import gravit.qa.global.annotation.Facade;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.job.service.JobQueryService;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.SprintDto;
import gravit.qa.sprint.service.SprintQueryService;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static gravit.qa.global.exception.domain.CustomErrorCode.CHECKLIST_FEATURE_NOT_IN_SPRINT;
import static gravit.qa.global.exception.domain.CustomErrorCode.INVALID_PARAMS;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_STATUS_CONFLICT;

@Facade
@RequiredArgsConstructor
public class ChecklistFacade {

    private final ChecklistQueryService checklistQueryService;
    private final ChecklistCommandService checklistCommandService;
    private final FeatureQueryService featureQueryService;
    private final SprintQueryService sprintQueryService;
    private final JobQueryService jobQueryService;

    public ChecklistResponse getChecklist(
            long sprintId,
            Integer round
    ) {
        Classification classification = Classification.ofRound(round)
                .orElseThrow(() -> new RestApiException(INVALID_PARAMS));
        SprintDto sprint = sprintQueryService.getSprint(sprintId);

        List<ChecklistItemResponse> items = checklistQueryService.getItems(sprintId, classification, sprint.status());
        Map<Long, List<ChecklistItemResponse>> featureIdToItems = items.stream()
                .collect(Collectors.groupingBy(ChecklistItemResponse::featureId, LinkedHashMap::new, Collectors.toList()));

        List<SprintFeatureDto> sprintFeatures = featureQueryService.getSprintFeatures(sprintId);
        Set<Long> sprintFeatureIds = sprintFeatures.stream().map(SprintFeatureDto::featureId).collect(Collectors.toSet());

        List<ChecklistFeatureResponse> features = new ArrayList<>();
        for (SprintFeatureDto feature : sprintFeatures) {
            List<ChecklistItemResponse> featureItems = featureIdToItems.getOrDefault(feature.featureId(), List.of());
            if (!featureItems.isEmpty()) {
                features.add(toFeature(feature.feature(), feature.changed(), featureItems));
            }
        }
        List<FeatureDto> remaining = featureQueryService.getFeatures(featureIdToItems.keySet().stream()
                .filter(featureId -> !sprintFeatureIds.contains(featureId))
                .toList());
        remaining.forEach(feature -> features.add(toFeature(feature, false, featureIdToItems.get(feature.id()))));

        return ChecklistResponse.of(sprintId, sprint.status(), features);
    }

    public ChecklistItemResponse createItem(
            long sprintId,
            ChecklistItemCreateRequest request
    ) {
        SprintStatus status = requireChecklistEditable(sprintId);
        if (!featureQueryService.isSprintFeature(sprintId, request.featureId())) {
            throw new RestApiException(CHECKLIST_FEATURE_NOT_IN_SPRINT);
        }

        return checklistCommandService.createItem(sprintId, request, status);
    }

    public ChecklistItemResponse updateItem(
            long itemId,
            ChecklistItemUpdateRequest request,
            long adminId
    ) {
        SprintStatus status = requireChecklistEditable(checklistQueryService.getSprintIdOfItem(itemId));

        return checklistCommandService.updateItem(itemId, request, adminId, status);
    }

    public void deleteItem(
            long itemId,
            int version
    ) {
        requireChecklistEditable(checklistQueryService.getSprintIdOfItem(itemId));

        checklistCommandService.deleteItem(itemId, version);
    }

    public CheckCellResponse updateCell(
            long itemId,
            Part part,
            CheckCellUpdateRequest request,
            long adminId
    ) {
        long sprintId = checklistQueryService.getSprintIdOfItem(itemId);
        if (sprintQueryService.getSprint(sprintId).status() != SprintStatus.MANUAL_REVIEW) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }

        return checklistCommandService.updateCell(itemId, part, request, adminId, SprintStatus.MANUAL_REVIEW);
    }

    private SprintStatus requireChecklistEditable(long sprintId) {
        SprintStatus status = sprintQueryService.getSprint(sprintId).status();
        if (!status.allowsChecklistEdit() || jobQueryService.hasActiveJob(sprintId)) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }
        return status;
    }

    private ChecklistFeatureResponse toFeature(
            FeatureDto feature,
            boolean changed,
            List<ChecklistItemResponse> items
    ) {
        List<CellState> states = items.stream()
                .flatMap(item -> item.cells().stream())
                .map(CheckCellResponse::state)
                .toList();
        return ChecklistFeatureResponse.of(feature.id(), feature.screenId(), feature.name(), changed, ProgressCalculator.completionRate(states), items);
    }
}
