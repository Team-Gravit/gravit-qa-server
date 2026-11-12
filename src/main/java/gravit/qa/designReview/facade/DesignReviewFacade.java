package gravit.qa.designReview.facade;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.support.ProgressCalculator;
import gravit.qa.designReview.dto.request.DesignReviewItemUpdateRequest;
import gravit.qa.designReview.dto.request.DesignReviewStateUpdateRequest;
import gravit.qa.designReview.dto.response.DesignReviewFeatureResponse;
import gravit.qa.designReview.dto.response.DesignReviewItemResponse;
import gravit.qa.designReview.dto.response.DesignReviewResponse;
import gravit.qa.designReview.service.DesignReviewCommandService;
import gravit.qa.designReview.service.DesignReviewQueryService;
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
import java.util.stream.Collectors;

import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_STATUS_CONFLICT;

@Facade
@RequiredArgsConstructor
public class DesignReviewFacade {

    private final DesignReviewQueryService designReviewQueryService;
    private final DesignReviewCommandService designReviewCommandService;
    private final FeatureQueryService featureQueryService;
    private final SprintQueryService sprintQueryService;
    private final JobQueryService jobQueryService;

    public DesignReviewResponse getDesignReview(long sprintId) {
        SprintDto sprint = sprintQueryService.getSprint(sprintId);

        List<DesignReviewItemResponse> items = designReviewQueryService.getItems(sprintId, isEditable(sprint.status()));
        Map<Long, List<DesignReviewItemResponse>> featureIdToItems = items.stream()
                .collect(Collectors.groupingBy(DesignReviewItemResponse::featureId, LinkedHashMap::new, Collectors.toList()));

        List<DesignReviewFeatureResponse> features = new ArrayList<>();
        for (SprintFeatureDto feature : featureQueryService.getSprintFeatures(sprintId)) {
            List<DesignReviewItemResponse> featureItems = featureIdToItems.getOrDefault(feature.featureId(), List.of());
            if (featureItems.isEmpty()) {
                continue;
            }
            List<CellState> states = featureItems.stream().map(DesignReviewItemResponse::state).toList();
            features.add(DesignReviewFeatureResponse.of(
                    feature.featureId(),
                    feature.screenId(),
                    feature.feature().name(),
                    feature.changed(),
                    ProgressCalculator.completionRate(states),
                    featureItems
            ));
        }
        return DesignReviewResponse.of(sprintId, sprint.status(), features);
    }

    public DesignReviewItemResponse updateState(
            long itemId,
            DesignReviewStateUpdateRequest request,
            long adminId
    ) {
        SprintDto sprint = sprintQueryService.getSprint(designReviewQueryService.getSprintIdOfItem(itemId));
        if (!isEditable(sprint.status())) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }

        return designReviewCommandService.updateState(itemId, request, adminId);
    }

    public DesignReviewItemResponse updateItem(
            long itemId,
            DesignReviewItemUpdateRequest request,
            long adminId
    ) {
        requireItemEditable(designReviewQueryService.getSprintIdOfItem(itemId));

        return designReviewCommandService.updateItem(itemId, request, adminId);
    }

    public void deleteItem(
            long itemId,
            int version
    ) {
        requireItemEditable(designReviewQueryService.getSprintIdOfItem(itemId));

        designReviewCommandService.deleteItem(itemId, version);
    }

    private void requireItemEditable(long sprintId) {
        if (!isEditable(sprintQueryService.getSprint(sprintId).status()) || jobQueryService.hasActiveJob(sprintId)) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }
    }

    private boolean isEditable(SprintStatus status) {
        return status == SprintStatus.MANUAL_REVIEW;
    }
}
