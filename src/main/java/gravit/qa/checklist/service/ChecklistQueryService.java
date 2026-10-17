package gravit.qa.checklist.service;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.domain.ReviewState;
import gravit.qa.checklist.dto.internal.CellStateCountDto;
import gravit.qa.checklist.dto.internal.SprintItemCountDto;
import gravit.qa.checklist.dto.response.CheckCellHistoryResponse;
import gravit.qa.checklist.dto.response.CheckCellResponse;
import gravit.qa.checklist.dto.response.ChecklistItemResponse;
import gravit.qa.checklist.dto.response.PartProgressResponse;
import gravit.qa.checklist.repository.CheckCellHistoryRepository;
import gravit.qa.checklist.repository.CheckCellRepository;
import gravit.qa.checklist.repository.ChecklistItemRepository;
import gravit.qa.checklist.support.CheckCellEditPolicy;
import gravit.qa.feature.domain.Part;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.sprint.domain.SprintStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static gravit.qa.global.exception.domain.CustomErrorCode.CHECKLIST_ITEM_NOT_FOUND;
import static gravit.qa.global.exception.domain.CustomErrorCode.CHECK_CELL_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ChecklistQueryService {

    private final ChecklistItemRepository checklistItemRepository;
    private final CheckCellRepository checkCellRepository;
    private final CheckCellHistoryRepository checkCellHistoryRepository;

    @Transactional(readOnly = true)
    public List<ChecklistItemResponse> getItems(
            long sprintId,
            SprintStatus sprintStatus
    ) {
        List<ChecklistItem> items = checklistItemRepository.findAllBySprintIdOrderByFeatureIdAscSortOrderAscIdAsc(sprintId);
        if (items.isEmpty()) {
            return List.of();
        }

        Map<Long, List<CheckCell>> itemIdToCells = checkCellRepository.findAllByItemIdIn(items.stream().map(ChecklistItem::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(CheckCell::getItemId));

        return items.stream()
                .map(item -> ChecklistItemResponse.of(item, toCellResponses(item, itemIdToCells.getOrDefault(item.getId(), List.of()), sprintStatus)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<PartProgressResponse>> getProgress(Collection<Long> sprintIds) {
        Map<Long, Map<Part, Map<CellState, Long>>> sprintIdToCounts = new HashMap<>();
        if (!sprintIds.isEmpty()) {
            for (CellStateCountDto count : checkCellRepository.countStatesBySprintIds(sprintIds, ReviewState.REJECTED)) {
                sprintIdToCounts.computeIfAbsent(count.sprintId(), id -> new EnumMap<>(Part.class))
                        .computeIfAbsent(count.part(), part -> new EnumMap<>(CellState.class))
                        .merge(count.state(), count.count(), Long::sum);
            }
        }

        Map<Long, List<PartProgressResponse>> sprintIdToProgress = new LinkedHashMap<>();
        for (Long sprintId : sprintIds) {
            Map<Part, Map<CellState, Long>> partToCounts = sprintIdToCounts.getOrDefault(sprintId, Map.of());
            sprintIdToProgress.put(sprintId, Arrays.stream(Part.values())
                    .map(part -> toProgress(part, partToCounts.getOrDefault(part, Map.of())))
                    .toList());
        }
        return sprintIdToProgress;
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> countProposed(Collection<Long> sprintIds) {
        if (sprintIds.isEmpty()) {
            return Map.of();
        }
        return checklistItemRepository.countBySprintIdsAndReviewState(sprintIds, ReviewState.PROPOSED).stream()
                .collect(Collectors.toMap(SprintItemCountDto::sprintId, SprintItemCountDto::count));
    }

    @Transactional(readOnly = true)
    public long countProposed(long sprintId) {
        return checklistItemRepository.countBySprintIdAndReviewState(sprintId, ReviewState.PROPOSED);
    }

    @Transactional(readOnly = true)
    public long countUnchecked(long sprintId) {
        return checkCellRepository.countBySprintIdAndState(sprintId, ReviewState.REJECTED, CellState.UNCHECKED);
    }

    @Transactional(readOnly = true)
    public boolean existsConfirmedAuto(long sprintId) {
        return checklistItemRepository.existsBySprintIdAndClassificationAndReviewState(sprintId, Classification.AUTO, ReviewState.CONFIRMED);
    }

    @Transactional(readOnly = true)
    public long getSprintIdOfItem(long itemId) {
        return checklistItemRepository.findById(itemId)
                .map(ChecklistItem::getSprintId)
                .orElseThrow(() -> new RestApiException(CHECKLIST_ITEM_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<CheckCellHistoryResponse> getCellHistory(
            long itemId,
            Part part
    ) {
        if (checkCellRepository.findByItemIdAndPart(itemId, part).isEmpty()) {
            throw new RestApiException(CHECK_CELL_NOT_FOUND);
        }
        return checkCellHistoryRepository.findAllByItemIdAndPartOrderByChangedAtDescIdDesc(itemId, part).stream()
                .map(CheckCellHistoryResponse::from)
                .toList();
    }

    private List<CheckCellResponse> toCellResponses(
            ChecklistItem item,
            List<CheckCell> cells,
            SprintStatus sprintStatus
    ) {
        return cells.stream()
                .sorted(Comparator.comparing(CheckCell::getPart))
                .map(cell -> CheckCellResponse.of(cell, CheckCellEditPolicy.isEditable(
                        sprintStatus,
                        item.getClassification(),
                        item.getReviewState(),
                        cell.getPart(),
                        cell.getState()
                )))
                .toList();
    }

    private PartProgressResponse toProgress(
            Part part,
            Map<CellState, Long> stateToCount
    ) {
        return PartProgressResponse.of(
                part,
                stateToCount.getOrDefault(CellState.PASS, 0L),
                stateToCount.getOrDefault(CellState.FAIL, 0L),
                stateToCount.getOrDefault(CellState.UNCHECKED, 0L),
                stateToCount.getOrDefault(CellState.NOT_APPLICABLE, 0L)
        );
    }
}
