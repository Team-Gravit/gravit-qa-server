package gravit.qa.checklist.service;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.checklist.domain.CheckCellHistory;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.domain.ReviewState;
import gravit.qa.checklist.dto.internal.ChecklistItemDraftDto;
import gravit.qa.checklist.dto.internal.ChecklistProposalDto;
import gravit.qa.checklist.dto.request.CheckCellUpdateRequest;
import gravit.qa.checklist.dto.request.ChecklistItemCreateRequest;
import gravit.qa.checklist.dto.request.ChecklistItemUpdateRequest;
import gravit.qa.checklist.dto.response.CheckCellResponse;
import gravit.qa.checklist.dto.response.ChecklistItemResponse;
import gravit.qa.checklist.repository.CheckCellHistoryRepository;
import gravit.qa.checklist.repository.CheckCellRepository;
import gravit.qa.checklist.repository.ChecklistItemRepository;
import gravit.qa.checklist.service.port.TestCatalog;
import gravit.qa.checklist.support.CheckCellEditPolicy;
import gravit.qa.feature.domain.Part;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.global.util.KstTimes;
import gravit.qa.persona.domain.PersonaKey;
import gravit.qa.sprint.domain.SprintStatus;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static gravit.qa.global.exception.domain.CustomErrorCode.CHECKLIST_ITEM_NOT_FOUND;
import static gravit.qa.global.exception.domain.CustomErrorCode.CHECK_CELL_NOT_FOUND;
import static gravit.qa.global.exception.domain.CustomErrorCode.CHECK_CELL_STATE_INVALID;
import static gravit.qa.global.exception.domain.CustomErrorCode.INVALID_PARAMS;
import static gravit.qa.global.exception.domain.CustomErrorCode.PERSONA_KEY_UNKNOWN;
import static gravit.qa.global.exception.domain.CustomErrorCode.TEST_REF_NOT_IN_CATALOG;

@Service
@RequiredArgsConstructor
public class ChecklistCommandService {

    private final ChecklistItemRepository checklistItemRepository;
    private final CheckCellRepository checkCellRepository;
    private final CheckCellHistoryRepository checkCellHistoryRepository;

    private final TestCatalog testCatalog;

    private final Clock clock;

    @Transactional
    public void replaceProposed(
            long sprintId,
            List<ChecklistProposalDto> proposals
    ) {
        checklistItemRepository.deleteProposed(sprintId, ReviewState.PROPOSED);

        for (ChecklistProposalDto proposal : proposals) {
            int sortOrder = checklistItemRepository.findMaxSortOrder(sprintId, proposal.featureId()) + 1;
            for (ChecklistItemDraftDto draft : proposal.drafts()) {
                ChecklistItem item = checklistItemRepository.save(ChecklistItem.propose(sprintId, proposal.featureId(), draft, proposal.sourceRefs(), sortOrder++));
                checkCellRepository.saveAll(CheckCell.initialize(item.getId(), item.getParts()));
            }
        }
    }

    @Transactional
    public ChecklistItemResponse createItem(
            long sprintId,
            ChecklistItemCreateRequest request
    ) {
        validatePersonaKey(request.personaKey());
        validateTestRef(request.testRef());
        validateAutoTestRef(request.classification(), request.testRef());

        int sortOrder = checklistItemRepository.findMaxSortOrder(sprintId, request.featureId()) + 1;
        ChecklistItem item = checklistItemRepository.save(ChecklistItem.createByHuman(
                sprintId,
                request.featureId(),
                request.title().trim(),
                request.expected().trim(),
                List.copyOf(request.parts()),
                request.classification(),
                request.testRef(),
                request.personaKey(),
                sortOrder
        ));
        List<CheckCell> cells = checkCellRepository.saveAll(CheckCell.initialize(item.getId(), item.getParts()));
        checklistItemRepository.flush();

        return toResponse(item, cells, SprintStatus.REVIEW);
    }

    @Transactional
    public ChecklistItemResponse updateItem(
            long itemId,
            ChecklistItemUpdateRequest request,
            long adminId
    ) {
        ChecklistItem item = findItem(itemId);
        item.requireVersion(request.version());

        if (request.title() != null) {
            item.changeTitle(request.title().trim());
        }
        if (request.expected() != null) {
            item.changeExpected(request.expected().trim());
        }
        if (isPresent(request.personaKey())) {
            String personaKey = request.personaKey().get();
            validatePersonaKey(personaKey);
            item.changePersonaKey(personaKey);
        }
        if (isPresent(request.testRef())) {
            String testRef = request.testRef().get();
            validateTestRef(testRef);
            item.changeTestRef(testRef);
        }
        if (request.classification() != null) {
            item.changeClassification(request.classification());
        }
        if (request.reviewState() != null) {
            if (request.reviewState() == ReviewState.PROPOSED) {
                throw new RestApiException(INVALID_PARAMS);
            }
            item.review(request.reviewState());
        }
        validateAutoTestRef(item.getClassification(), item.getTestRef());

        List<CheckCell> cells = checkCellRepository.findAllByItemId(itemId);
        if (request.parts() != null && !Set.copyOf(item.getParts()).equals(request.parts())) {
            item.changeParts(List.copyOf(request.parts()));
            applyParts(item, cells, adminId);
        }
        checklistItemRepository.flush();

        return toResponse(item, cells, SprintStatus.REVIEW);
    }

    @Transactional
    public CheckCellResponse updateCell(
            long itemId,
            Part part,
            CheckCellUpdateRequest request,
            long adminId
    ) {
        if (request.state() == CellState.NOT_APPLICABLE) {
            throw new RestApiException(CHECK_CELL_STATE_INVALID);
        }

        ChecklistItem item = findItem(itemId);
        CheckCell cell = checkCellRepository.findByItemIdAndPart(itemId, part)
                .orElseThrow(() -> new RestApiException(CHECK_CELL_NOT_FOUND));
        CheckCellEditPolicy.requireEditable(item.getClassification(), item.getReviewState(), part, cell.getState());
        cell.requireVersion(request.version());

        if (cell.getState() != request.state()) {
            checkCellHistoryRepository.save(cell.check(request.state(), adminId, now()));
            checkCellRepository.flush();
        }

        return CheckCellResponse.of(cell, CheckCellEditPolicy.isEditable(
                SprintStatus.IN_PROGRESS,
                item.getClassification(),
                item.getReviewState(),
                part,
                cell.getState()
        ));
    }

    private void applyParts(
            ChecklistItem item,
            List<CheckCell> cells,
            long adminId
    ) {
        LocalDateTime now = now();
        List<CheckCellHistory> histories = new ArrayList<>();
        for (CheckCell cell : cells) {
            cell.applyParts(item.getParts(), adminId, now).ifPresent(histories::add);
        }
        checkCellHistoryRepository.saveAll(histories);
    }

    private void validatePersonaKey(String personaKey) {
        if (personaKey != null && !PersonaKey.isKnown(personaKey)) {
            throw new RestApiException(PERSONA_KEY_UNKNOWN);
        }
    }

    private void validateTestRef(String testRef) {
        if (testRef != null && !testCatalog.contains(testRef)) {
            throw new RestApiException(TEST_REF_NOT_IN_CATALOG);
        }
    }

    private void validateAutoTestRef(
            Classification classification,
            String testRef
    ) {
        if (classification == Classification.AUTO && !testCatalog.contains(testRef)) {
            throw new RestApiException(TEST_REF_NOT_IN_CATALOG);
        }
    }

    private boolean isPresent(JsonNullable<String> value) {
        return value != null && value.isPresent();
    }

    private ChecklistItem findItem(long itemId) {
        return checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new RestApiException(CHECKLIST_ITEM_NOT_FOUND));
    }

    private ChecklistItemResponse toResponse(
            ChecklistItem item,
            List<CheckCell> cells,
            SprintStatus sprintStatus
    ) {
        List<CheckCellResponse> cellResponses = cells.stream()
                .sorted(Comparator.comparing(CheckCell::getPart))
                .map(cell -> CheckCellResponse.of(cell, CheckCellEditPolicy.isEditable(
                        sprintStatus,
                        item.getClassification(),
                        item.getReviewState(),
                        cell.getPart(),
                        cell.getState()
                )))
                .toList();
        return ChecklistItemResponse.of(item, cellResponses);
    }

    private LocalDateTime now() {
        return KstTimes.now(clock);
    }
}
