package gravit.qa.designReview.service;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.dto.internal.FailRecordDto;
import gravit.qa.designReview.domain.DesignReviewItem;
import gravit.qa.designReview.domain.DesignReviewItemHistory;
import gravit.qa.designReview.dto.internal.FailedDesignItemDto;
import gravit.qa.designReview.dto.response.DesignReviewHistoryResponse;
import gravit.qa.designReview.dto.response.DesignReviewItemResponse;
import gravit.qa.designReview.repository.DesignReviewItemHistoryRepository;
import gravit.qa.designReview.repository.DesignReviewItemRepository;
import gravit.qa.global.exception.domain.RestApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static gravit.qa.global.exception.domain.CustomErrorCode.DESIGN_REVIEW_ITEM_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class DesignReviewQueryService {

    private final DesignReviewItemRepository designReviewItemRepository;
    private final DesignReviewItemHistoryRepository designReviewItemHistoryRepository;

    @Transactional(readOnly = true)
    public List<DesignReviewItemResponse> getItems(
            long sprintId,
            boolean editable
    ) {
        return designReviewItemRepository.findAllBySprintIdOrderByFeatureIdAscPlatformAscSortOrderAscIdAsc(sprintId).stream()
                .map(item -> DesignReviewItemResponse.of(item, editable))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<CellState, Long> countStates(long sprintId) {
        Map<CellState, Long> stateToCount = new EnumMap<>(CellState.class);
        designReviewItemRepository.findAllBySprintId(sprintId)
                .forEach(item -> stateToCount.merge(item.getState(), 1L, Long::sum));
        return stateToCount;
    }

    @Transactional(readOnly = true)
    public long countUnchecked(long sprintId) {
        return designReviewItemRepository.countBySprintIdAndState(sprintId, CellState.UNCHECKED);
    }

    @Transactional(readOnly = true)
    public List<FailedDesignItemDto> getFailedItems(long sprintId) {
        List<DesignReviewItem> items = designReviewItemRepository.findAllBySprintIdAndState(sprintId, CellState.FAIL);
        if (items.isEmpty()) {
            return List.of();
        }

        Map<Long, List<DesignReviewItemHistory>> itemIdToHistories = designReviewItemHistoryRepository.findAllByItemIdInOrderByChangedAtDescIdDesc(items.stream().map(DesignReviewItem::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(DesignReviewItemHistory::getItemId));

        return items.stream()
                .map(item -> FailedDesignItemDto.of(item, failRecordsSinceReset(itemIdToHistories.getOrDefault(item.getId(), List.of()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public long getSprintIdOfItem(long itemId) {
        return designReviewItemRepository.findById(itemId)
                .map(DesignReviewItem::getSprintId)
                .orElseThrow(() -> new RestApiException(DESIGN_REVIEW_ITEM_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<DesignReviewHistoryResponse> getHistory(long itemId) {
        if (!designReviewItemRepository.existsById(itemId)) {
            throw new RestApiException(DESIGN_REVIEW_ITEM_NOT_FOUND);
        }
        return designReviewItemHistoryRepository.findAllByItemIdOrderByChangedAtDescIdDesc(itemId).stream()
                .map(DesignReviewHistoryResponse::from)
                .toList();
    }

    private List<FailRecordDto> failRecordsSinceReset(List<DesignReviewItemHistory> newestFirst) {
        List<FailRecordDto> records = new ArrayList<>();
        for (DesignReviewItemHistory history : newestFirst) {
            if (isEditReset(history)) {
                break;
            }
            if (history.getToState() == CellState.FAIL) {
                records.add(FailRecordDto.of(history.getReason(), List.of()));
            }
        }
        return records;
    }

    private boolean isEditReset(DesignReviewItemHistory history) {
        return history.getToState() != CellState.FAIL && DesignReviewCommandService.EDIT_RESET_REASON.equals(history.getReason());
    }
}
