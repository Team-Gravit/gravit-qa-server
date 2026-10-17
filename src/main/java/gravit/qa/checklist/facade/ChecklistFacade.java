package gravit.qa.checklist.facade;

import gravit.qa.checklist.dto.request.CheckCellUpdateRequest;
import gravit.qa.checklist.dto.request.ChecklistItemCreateRequest;
import gravit.qa.checklist.dto.request.ChecklistItemUpdateRequest;
import gravit.qa.checklist.dto.response.CheckCellResponse;
import gravit.qa.checklist.dto.response.ChecklistFeatureResponse;
import gravit.qa.checklist.dto.response.ChecklistItemResponse;
import gravit.qa.checklist.dto.response.ChecklistResponse;
import gravit.qa.checklist.service.ChecklistCommandService;
import gravit.qa.checklist.service.ChecklistQueryService;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.dto.internal.FeatureDto;
import gravit.qa.feature.dto.internal.SprintFeatureDto;
import gravit.qa.feature.dto.response.FeatureResponse;
import gravit.qa.feature.service.FeatureQueryService;
import gravit.qa.global.annotation.Facade;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.SprintDto;
import gravit.qa.sprint.dto.internal.WarningDto;
import gravit.qa.sprint.service.SprintQueryService;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static gravit.qa.global.exception.domain.CustomErrorCode.CHECKLIST_FEATURE_NOT_IN_SPRINT;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_STATUS_CONFLICT;

@Facade
@RequiredArgsConstructor
public class ChecklistFacade {

    private final ChecklistQueryService checklistQueryService;
    private final ChecklistCommandService checklistCommandService;
    private final FeatureQueryService featureQueryService;
    private final SprintQueryService sprintQueryService;

    public ChecklistResponse getChecklist(long sprintId) {
        SprintDto sprint = sprintQueryService.getSprint(sprintId);

        List<ChecklistItemResponse> items = checklistQueryService.getItems(sprintId, sprint.status());
        Map<Long, List<ChecklistItemResponse>> featureIdToItems = items.stream()
                .collect(Collectors.groupingBy(ChecklistItemResponse::featureId, LinkedHashMap::new, Collectors.toList()));

        List<SprintFeatureDto> sprintFeatures = featureQueryService.getSprintFeatures(sprintId);
        Set<Long> sprintFeatureIds = sprintFeatures.stream().map(sprintFeature -> sprintFeature.feature().id()).collect(Collectors.toSet());
        List<FeatureDto> remainingFeatures = featureQueryService.getFeatures(featureIdToItems.keySet().stream()
                .filter(featureId -> !sprintFeatureIds.contains(featureId))
                .toList());

        List<ChecklistFeatureResponse> features = new ArrayList<>();
        for (SprintFeatureDto sprintFeature : sprintFeatures) {
            features.add(ChecklistFeatureResponse.of(
                    FeatureResponse.of(sprintFeature.feature(), sprintFeature.figmaNodeIds()),
                    warningCodes(sprintFeature.warnings()),
                    featureIdToItems.getOrDefault(sprintFeature.feature().id(), List.of())
            ));
        }
        for (FeatureDto feature : remainingFeatures) {
            features.add(ChecklistFeatureResponse.of(
                    FeatureResponse.of(feature, List.of()),
                    List.of(),
                    featureIdToItems.getOrDefault(feature.id(), List.of())
            ));
        }

        return ChecklistResponse.of(sprintId, sprint.status(), features);
    }

    public ChecklistItemResponse createItem(
            long sprintId,
            ChecklistItemCreateRequest request
    ) {
        requireStatus(sprintId, SprintStatus.REVIEW);
        if (!featureQueryService.isSprintFeature(sprintId, request.featureId())) {
            throw new RestApiException(CHECKLIST_FEATURE_NOT_IN_SPRINT);
        }

        return checklistCommandService.createItem(sprintId, request);
    }

    public ChecklistItemResponse updateItem(
            long itemId,
            ChecklistItemUpdateRequest request,
            long adminId
    ) {
        requireStatus(checklistQueryService.getSprintIdOfItem(itemId), SprintStatus.REVIEW);

        return checklistCommandService.updateItem(itemId, request, adminId);
    }

    public CheckCellResponse updateCell(
            long itemId,
            Part part,
            CheckCellUpdateRequest request,
            long adminId
    ) {
        requireStatus(checklistQueryService.getSprintIdOfItem(itemId), SprintStatus.IN_PROGRESS);

        return checklistCommandService.updateCell(itemId, part, request, adminId);
    }

    private void requireStatus(
            long sprintId,
            SprintStatus expected
    ) {
        if (sprintQueryService.getSprint(sprintId).status() != expected) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }
    }

    private List<String> warningCodes(List<WarningDto> warnings) {
        return warnings.stream()
                .map(warning -> warning.code().name())
                .distinct()
                .toList();
    }
}
