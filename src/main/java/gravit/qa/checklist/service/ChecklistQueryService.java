package gravit.qa.checklist.service;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.CellStateCountDto;
import gravit.qa.checklist.dto.internal.ChecklistItemDto;
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

import java.util.Comparator;
import java.util.EnumMap;
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
            List<CheckCell> cells,
            SprintStatus sprintStatus
    ) {
        return cells.stream()
                .sorted(Comparator.comparing(CheckCell::getPart))
                .map(cell -> CheckCellResponse.of(cell, CheckCellEditPolicy.isEditable(sprintStatus, cell.getState())))
                .toList();
    }
}
