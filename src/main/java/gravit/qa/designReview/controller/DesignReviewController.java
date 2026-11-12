package gravit.qa.designReview.controller;

import gravit.qa.designReview.controller.docs.DesignReviewControllerDocs;
import gravit.qa.designReview.dto.request.DesignReviewItemUpdateRequest;
import gravit.qa.designReview.dto.request.DesignReviewStateUpdateRequest;
import gravit.qa.designReview.dto.response.DesignReviewHistoryResponse;
import gravit.qa.designReview.dto.response.DesignReviewItemResponse;
import gravit.qa.designReview.dto.response.DesignReviewResponse;
import gravit.qa.designReview.facade.DesignReviewFacade;
import gravit.qa.designReview.service.DesignReviewQueryService;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.security.principal.LoginAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static gravit.qa.global.exception.domain.CustomErrorCode.INVALID_PARAMS;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class DesignReviewController implements DesignReviewControllerDocs {

    private final DesignReviewFacade designReviewFacade;

    private final DesignReviewQueryService designReviewQueryService;

    @GetMapping("/sprints/{sprintId}/design-review")
    public ResponseEntity<DesignReviewResponse> getDesignReview(@PathVariable("sprintId") long sprintId) {
        return ResponseEntity.status(OK).body(designReviewFacade.getDesignReview(sprintId));
    }

    @PutMapping("/design-review-items/{itemId}")
    public ResponseEntity<DesignReviewItemResponse> updateDesignReviewItemState(
            @PathVariable("itemId") long itemId,
            @Valid @RequestBody DesignReviewStateUpdateRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    ) {
        return ResponseEntity.status(OK).body(designReviewFacade.updateState(itemId, request, loginAdmin.adminId()));
    }

    @PatchMapping("/design-review-items/{itemId}")
    public ResponseEntity<DesignReviewItemResponse> updateDesignReviewItem(
            @PathVariable("itemId") long itemId,
            @Valid @RequestBody DesignReviewItemUpdateRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    ) {
        return ResponseEntity.status(OK).body(designReviewFacade.updateItem(itemId, request, loginAdmin.adminId()));
    }

    @DeleteMapping("/design-review-items/{itemId}")
    public ResponseEntity<Void> deleteDesignReviewItem(
            @PathVariable("itemId") long itemId,
            @RequestParam(name = "version", required = false) Integer version
    ) {
        if (version == null) {
            throw new RestApiException(INVALID_PARAMS);
        }
        designReviewFacade.deleteItem(itemId, version);
        return ResponseEntity.status(NO_CONTENT).build();
    }

    @GetMapping("/design-review-items/{itemId}/history")
    public ResponseEntity<List<DesignReviewHistoryResponse>> listDesignReviewItemHistory(@PathVariable("itemId") long itemId) {
        return ResponseEntity.status(OK).body(designReviewQueryService.getHistory(itemId));
    }
}
