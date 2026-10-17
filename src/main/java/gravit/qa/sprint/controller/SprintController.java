package gravit.qa.sprint.controller;

import gravit.qa.job.dto.response.JobResponse;
import gravit.qa.security.principal.LoginAdmin;
import gravit.qa.sprint.controller.docs.SprintControllerDocs;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.request.SprintCloseRequest;
import gravit.qa.sprint.dto.request.SprintCreateRequest;
import gravit.qa.sprint.dto.response.SprintResponse;
import gravit.qa.sprint.dto.response.SprintSummaryResponse;
import gravit.qa.sprint.facade.SetupFacade;
import gravit.qa.sprint.facade.SprintFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sprints")
public class SprintController implements SprintControllerDocs {

    private final SprintFacade sprintFacade;
    private final SetupFacade setupFacade;

    @GetMapping
    public ResponseEntity<List<SprintSummaryResponse>> listSprints(@RequestParam(name = "status", required = false) SprintStatus status) {
        return ResponseEntity.status(OK).body(sprintFacade.listSprints(status));
    }

    @PostMapping
    public ResponseEntity<SprintResponse> createSprint(
            @Valid @RequestBody SprintCreateRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    ) {
        return ResponseEntity.status(CREATED).body(sprintFacade.createSprint(request, loginAdmin.adminId()));
    }

    @GetMapping("/{sprintId}")
    public ResponseEntity<SprintResponse> getSprint(@PathVariable("sprintId") long sprintId) {
        return ResponseEntity.status(OK).body(sprintFacade.getSprint(sprintId));
    }

    @PostMapping("/{sprintId}/close")
    public ResponseEntity<SprintResponse> closeSprint(
            @PathVariable("sprintId") long sprintId,
            @Valid @RequestBody SprintCloseRequest request
    ) {
        return ResponseEntity.status(OK).body(sprintFacade.closeSprint(sprintId, request));
    }

    @PostMapping("/{sprintId}/setup-jobs")
    public ResponseEntity<JobResponse> startSetupJob(@PathVariable("sprintId") long sprintId) {
        return ResponseEntity.status(ACCEPTED).body(setupFacade.startSetup(sprintId));
    }
}
