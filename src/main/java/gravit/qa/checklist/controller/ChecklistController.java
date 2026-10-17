package gravit.qa.checklist.controller;

import gravit.qa.checklist.controller.docs.ChecklistControllerDocs;
import gravit.qa.checklist.dto.request.ChecklistConfirmRequest;
import gravit.qa.checklist.dto.request.ChecklistItemCreateRequest;
import gravit.qa.checklist.dto.response.ChecklistConfirmResponse;
import gravit.qa.checklist.dto.response.ChecklistItemResponse;
import gravit.qa.checklist.dto.response.ChecklistResponse;
import gravit.qa.checklist.facade.ChecklistFacade;
import gravit.qa.sprint.facade.SprintFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sprints/{sprintId}")
public class ChecklistController implements ChecklistControllerDocs {

    private final SprintFacade sprintFacade;
    private final ChecklistFacade checklistFacade;

    @GetMapping("/checklist")
    public ResponseEntity<ChecklistResponse> getChecklist(@PathVariable("sprintId") long sprintId) {
        return ResponseEntity.status(OK).body(checklistFacade.getChecklist(sprintId));
    }

    @PostMapping("/checklist-items")
    public ResponseEntity<ChecklistItemResponse> createChecklistItem(
            @PathVariable("sprintId") long sprintId,
            @Valid @RequestBody ChecklistItemCreateRequest request
    ) {
        return ResponseEntity.status(CREATED).body(checklistFacade.createItem(sprintId, request));
    }

    @PostMapping("/checklist/confirm")
    public ResponseEntity<ChecklistConfirmResponse> confirmChecklist(
            @PathVariable("sprintId") long sprintId,
            @Valid @RequestBody ChecklistConfirmRequest request
    ) {
        return ResponseEntity.status(OK).body(sprintFacade.confirmChecklist(sprintId, request));
    }
}
