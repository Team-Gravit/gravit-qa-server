package gravit.qa.checklist.controller;

import gravit.qa.checklist.controller.docs.ChecklistItemControllerDocs;
import gravit.qa.checklist.dto.request.CheckCellUpdateRequest;
import gravit.qa.checklist.dto.request.ChecklistItemUpdateRequest;
import gravit.qa.checklist.dto.response.CheckCellHistoryResponse;
import gravit.qa.checklist.dto.response.CheckCellResponse;
import gravit.qa.checklist.dto.response.ChecklistItemResponse;
import gravit.qa.checklist.facade.ChecklistFacade;
import gravit.qa.checklist.service.ChecklistQueryService;
import gravit.qa.feature.domain.Part;
import gravit.qa.security.principal.LoginAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/checklist-items")
public class ChecklistItemController implements ChecklistItemControllerDocs {

    private final ChecklistFacade checklistFacade;

    private final ChecklistQueryService checklistQueryService;

    @PatchMapping("/{itemId}")
    public ResponseEntity<ChecklistItemResponse> updateChecklistItem(
            @PathVariable("itemId") long itemId,
            @Valid @RequestBody ChecklistItemUpdateRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    ) {
        return ResponseEntity.status(OK).body(checklistFacade.updateItem(itemId, request, loginAdmin.adminId()));
    }

    @PutMapping("/{itemId}/cells/{part}")
    public ResponseEntity<CheckCellResponse> updateCheckCell(
            @PathVariable("itemId") long itemId,
            @PathVariable("part") Part part,
            @Valid @RequestBody CheckCellUpdateRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    ) {
        return ResponseEntity.status(OK).body(checklistFacade.updateCell(itemId, part, request, loginAdmin.adminId()));
    }

    @GetMapping("/{itemId}/cells/{part}/history")
    public ResponseEntity<List<CheckCellHistoryResponse>> listCheckCellHistory(
            @PathVariable("itemId") long itemId,
            @PathVariable("part") Part part
    ) {
        return ResponseEntity.status(OK).body(checklistQueryService.getCellHistory(itemId, part));
    }
}
