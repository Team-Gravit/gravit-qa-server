package gravit.qa.checklist.service;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.checklist.domain.CheckCellHistory;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.CellStateCountDto;
import gravit.qa.checklist.dto.internal.ChecklistItemDto;
import gravit.qa.checklist.dto.internal.FailRecordDto;
import gravit.qa.checklist.dto.internal.FailedCellDto;
import gravit.qa.checklist.dto.response.CheckCellHistoryResponse;
import gravit.qa.checklist.dto.response.CheckCellResponse;
import gravit.qa.checklist.dto.response.ChecklistItemResponse;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
            Classification classification,
            SprintStatus sprintStatus
    ) {
        List<ChecklistItem> items = checklistItemRepository.findAllBySprintIdAndClassificationOrderByFeatureIdAscSortOrderAscIdAsc(sprintId, classification);
        if (items.isEmpty()) {
            return List.of();
        }

        Map<Long, List<CheckCell>> itemIdToCells = checkCellRepository.findAllByItemIdIn(items.stream().map(ChecklistItem::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(CheckCell::getItemId));

        return items.stream()
                .map(item -> ChecklistItemResponse.of(item, toCellResponses(itemIdToCells.getOrDefault(item.getId(), List.of()), sprintStatus)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChecklistItemDto> getItems(
            long sprintId,
            Classification classification
    ) {
        return checklistItemRepository.findAllBySprintIdAndClassificationOrderByFeatureIdAscSortOrderAscIdAsc(sprintId, classification).stream()
                .map(ChecklistItemDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Part, Map<CellState, Long>> countStates(long sprintId) {
        Map<Part, Map<CellState, Long>> partToCounts = new EnumMap<>(Part.class);
        for (CellStateCountDto count : checkCellRepository.countStatesBySprintId(sprintId)) {
            partToCounts.computeIfAbsent(count.part(), part -> new EnumMap<>(CellState.class))
                    .merge(count.state(), count.count(), Long::sum);
        }
        return partToCounts;
    }

    @Transactional(readOnly = true)
    public long countUnchecked(long sprintId) {
        return checkCellRepository.countBySprintIdAndState(sprintId, CellState.UNCHECKED);
    }

    @Transactional(readOnly = true)
    public Map<Classification, Long> countUncheckedByClassification(long sprintId) {
        Map<Classification, Long> classificationToCount = new EnumMap<>(Classification.class);
        checkCellRepository.countClassificationsBySprintIdAndState(sprintId, CellState.UNCHECKED)
                .forEach(count -> classificationToCount.put(count.classification(), count.count()));
        return classificationToCount;
    }

    @Transactional(readOnly = true)
    public List<FailedCellDto> getFailedCells(
            long sprintId,
            Part part
    ) {
        List<CheckCell> cells = checkCellRepository.findAllBySprintIdAndPartAndState(sprintId, part, CellState.FAIL);
        if (cells.isEmpty()) {
            return List.of();
        }

        List<Long> itemIds = cells.stream().map(CheckCell::getItemId).toList();
        Map<Long, ChecklistItem> itemIdToItem = checklistItemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(ChecklistItem::getId, Function.identity()));
        Map<Long, List<CheckCellHistory>> itemIdToHistories = checkCellHistoryRepository.findAllByItemIdInAndPartOrderByChangedAtDescIdDesc(itemIds, part).stream()
                .collect(Collectors.groupingBy(CheckCellHistory::getItemId));

        return cells.stream()
                .map(cell -> FailedCellDto.of(
                        itemIdToItem.get(cell.getItemId()),
                        cell,
                        failRecordsSinceReset(itemIdToHistories.getOrDefault(cell.getItemId(), List.of()))
                ))
                .toList();
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

    private List<FailRecordDto> failRecordsSinceReset(List<CheckCellHistory> newestFirst) {
        List<FailRecordDto> records = new ArrayList<>();
        for (CheckCellHistory history : newestFirst) {
            if (isEditReset(history)) {
                break;
            }
            if (history.getToState() == CellState.FAIL) {
                records.add(FailRecordDto.of(history.getReason(), history.getEvidence()));
            }
        }
        return records;
    }

    private boolean isEditReset(CheckCellHistory history) {
        return history.getToState() != CellState.FAIL && ChecklistCommandService.EDIT_RESET_REASON.equals(history.getReason());
    }

    private List<CheckCellResponse> toCellResponses(
            List<CheckCell> cells,
            SprintStatus sprintStatus
    ) {
        return cells.stream()
                .sorted(Comparator.comparing(CheckCell::getPart))
                .map(cell -> CheckCellResponse.of(cell, CheckCellEditPolicy.isEditable(sprintStatus, cell.getState())))
                .toList();
    }
}
