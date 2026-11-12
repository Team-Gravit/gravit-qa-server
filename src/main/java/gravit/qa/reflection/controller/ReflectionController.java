package gravit.qa.reflection.controller;

import gravit.qa.reflection.controller.docs.ReflectionControllerDocs;
import gravit.qa.reflection.dto.request.ReflectionDecisionRequest;
import gravit.qa.reflection.dto.response.ReflectionResponse;
import gravit.qa.reflection.facade.ReflectionFacade;
import gravit.qa.security.principal.LoginAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ReflectionController implements ReflectionControllerDocs {

    private final ReflectionFacade reflectionFacade;

    @GetMapping("/sprints/{sprintId}/reflections")
    public ResponseEntity<List<ReflectionResponse>> listReflections(@PathVariable("sprintId") long sprintId) {
        return ResponseEntity.status(OK).body(reflectionFacade.getReflections(sprintId));
    }

    @PostMapping("/reflections/{reflectionId}/approve")
    public ResponseEntity<ReflectionResponse> approveReflection(
            @PathVariable("reflectionId") long reflectionId,
            @Valid @RequestBody ReflectionDecisionRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    ) {
        return ResponseEntity.status(OK).body(reflectionFacade.approve(reflectionId, request.version(), loginAdmin.adminId()));
    }

    @PostMapping("/reflections/{reflectionId}/reject")
    public ResponseEntity<ReflectionResponse> rejectReflection(
            @PathVariable("reflectionId") long reflectionId,
            @Valid @RequestBody ReflectionDecisionRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    ) {
        return ResponseEntity.status(OK).body(reflectionFacade.reject(reflectionId, request.version(), loginAdmin.adminId()));
    }
}
