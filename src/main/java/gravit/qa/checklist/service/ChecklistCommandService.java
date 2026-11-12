package gravit.qa.checklist.service;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.checklist.domain.CheckCellHistory;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.dto.internal.CellJudgmentDto;
import gravit.qa.checklist.dto.internal.ChecklistItemDraftDto;
import gravit.qa.checklist.dto.request.CheckCellUpdateRequest;
import gravit.qa.checklist.dto.request.ChecklistItemCreateRequest;
import gravit.qa.checklist.dto.request.ChecklistItemUpdateRequest;
import gravit.qa.checklist.dto.response.CheckCellResponse;
import gravit.qa.checklist.dto.response.ChecklistItemResponse;
import gravit.qa.checklist.repository.CheckCellHistoryRepository;
import gravit.qa.checklist.repository.CheckCellRepository;
import gravit.qa.checklist.repository.ChecklistItemRepository;
import gravit.qa.checklist.support.CheckCellEditPolicy;
import gravit.qa.feature.domain.Part;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.global.util.KstTimes;
import gravit.qa.sprint.domain.SprintStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static gravit.qa.global.exception.domain.CustomErrorCode.CHECKLIST_ITEM_NOT_FOUND;
import static gravit.qa.global.exception.domain.CustomErrorCode.CHECK_CELL_NOT_FOUND;
import static gravit.qa.global.exception.domain.CustomErrorCode.CHECK_CELL_STATE_INVALID;

@Service
@RequiredArgsConstructor
public class ChecklistCommandService {

    public static final String EDIT_RESET_REASON = "항목 수정으로 판정 초기화";

    private final ChecklistItemRepository checklistItemRepository;
    private final CheckCellRepository checkCellRepository;
    private final CheckCellHistoryRepository checkCellHistoryRepository;

    private final Clock clock;

    @Transactional
    public void saveGenerated(
            long sprintId,
            long featureId,
            List<ChecklistItemDraftDto> drafts,
            List<Long> sourceRefs
    ) {
        int sortOrder = checklistItemRepository.findMaxSortOrder(sprintId, featureId) + 1;
        for (ChecklistItemDraftDto draft : drafts) {
            ChecklistItem item = checklistItemRepository.save(ChecklistItem.generate(sprintId, featureId, draft, sourceRefs, sortOrder++));
            checkCellRepository.saveAll(CheckCell.initialize(item.getId(), item.getParts()));
        }
    }

    @Transactional
    public ChecklistItemResponse createItem(
            long sprintId,
            ChecklistItemCreateRequest request,
            SprintStatus sprintStatus
    ) {
        int sortOrder = checklistItemRepository.findMaxSortOrder(sprintId, request.featureId()) + 1;
        ChecklistItem item = checklistItemRepository.save(ChecklistItem.createByHuman(
                sprintId,
                request.featureId(),
                request.title().trim(),
                request.expected().trim(),
                List.copyOf(request.parts()),
                request.classification(),
                sortOrder
        ));
        List<CheckCell> cells = checkCellRepository.saveAll(CheckCell.initialize(item.getId(), item.getParts()));
        checklistItemRepository.flush();

        return toResponse(item, cells, sprintStatus);
    }

    @Transactional
    public ChecklistItemResponse updateItem(
            long itemId,
            ChecklistItemUpdateRequest request,
            long adminId,
            SprintStatus sprintStatus
    ) {
        ChecklistItem item = findItem(itemId);
        item.requireVersion(request.version());

        boolean contentChanged = false;
        if (request.title() != null && !request.title().trim().equals(item.getTitle())) {
            item.changeTitle(request.title().trim());
            contentChanged = true;
        }
        if (request.expected() != null && !request.expected().trim().equals(item.getExpected())) {
            item.changeExpected(request.expected().trim());
            contentChanged = true;
        }
        if (request.classification() != null && request.classification() != item.getClassification()) {
            item.changeClassification(request.classification());
            contentChanged = true;
        }

        LocalDateTime now = now();
        List<CheckCell> cells = checkCellRepository.findAllByItemId(itemId);
        List<CheckCellHistory> histories = new ArrayList<>();
        if (request.parts() != null && !Set.copyOf(item.getParts()).equals(request.parts())) {
            item.changeParts(List.copyOf(request.parts()));
            cells.forEach(cell -> cell.applyParts(item.getParts(), EDIT_RESET_REASON, adminId, now).ifPresent(histories::add));
            contentChanged = true;
        }
        if (contentChanged) {
            cells.forEach(cell -> cell.resetJudgment(EDIT_RESET_REASON, adminId, now).ifPresent(histories::add));
        }
        checkCellHistoryRepository.saveAll(histories);
        checklistItemRepository.flush();

        return toResponse(item, cells, sprintStatus);
    }

    @Transactional
    public void deleteItem(
            long itemId,
            int version
    ) {
        ChecklistItem item = findItem(itemId);
        item.requireVersion(version);

        checklistItemRepository.delete(item);
        checklistItemRepository.flush();
    }

    @Transactional
    public CheckCellResponse updateCell(
            long itemId,
            Part part,
            CheckCellUpdateRequest request,
            long adminId,
            SprintStatus sprintStatus
    ) {
        if (request.state() == CellState.NOT_APPLICABLE) {
            throw new RestApiException(CHECK_CELL_STATE_INVALID);
        }

        findItem(itemId);
        CheckCell cell = findCell(itemId, part);
        CheckCellEditPolicy.requireEditable(cell.getState());
        cell.requireVersion(request.version());

        if (cell.getState() != request.state()) {
            checkCellHistoryRepository.save(cell.check(request.state(), adminId, now()));
            checkCellRepository.flush();
        }

        return CheckCellResponse.of(cell, CheckCellEditPolicy.isEditable(sprintStatus, cell.getState()));
    }

    @Transactional
    public void judgeCells(List<CellJudgmentDto> judgments) {
        LocalDateTime now = now();
        List<CheckCellHistory> histories = new ArrayList<>();
        for (CellJudgmentDto judgment : judgments) {
            CheckCell cell = findCell(judgment.itemId(), judgment.part());
            if (cell.isApplicable()) {
                histories.add(cell.judge(judgment.state(), judgment.reason(), now));
            }
        }
        checkCellHistoryRepository.saveAll(histories);
    }

    private ChecklistItem findItem(long itemId) {
        return checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new RestApiException(CHECKLIST_ITEM_NOT_FOUND));
    }

    private CheckCell findCell(
            long itemId,
            Part part
    ) {
        return checkCellRepository.findByItemIdAndPart(itemId, part)
                .orElseThrow(() -> new RestApiException(CHECK_CELL_NOT_FOUND));
    }

    private ChecklistItemResponse toResponse(
            ChecklistItem item,
            List<CheckCell> cells,
            SprintStatus sprintStatus
    ) {
        List<CheckCellResponse> cellResponses = cells.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(CheckCell::getPart))
                .map(cell -> CheckCellResponse.of(cell, CheckCellEditPolicy.isEditable(sprintStatus, cell.getState())))
                .toList();
        return ChecklistItemResponse.of(item, cellResponses);
    }

    private LocalDateTime now() {
        return KstTimes.now(clock);
    }
}
