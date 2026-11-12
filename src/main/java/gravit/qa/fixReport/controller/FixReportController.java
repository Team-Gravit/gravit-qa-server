package gravit.qa.fixReport.controller;

import gravit.qa.fixReport.controller.docs.FixReportControllerDocs;
import gravit.qa.fixReport.dto.internal.FixReportFileDto;
import gravit.qa.fixReport.dto.response.FixReportResponse;
import gravit.qa.fixReport.facade.FixReportFacade;
import gravit.qa.fixReport.support.FixReportFileName;
import gravit.qa.sprint.domain.ProgressPart;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sprints/{sprintId}/fix-reports")
public class FixReportController implements FixReportControllerDocs {

    private static final MediaType TEXT_PLAIN_UTF_8 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    private final FixReportFacade fixReportFacade;

    @GetMapping
    public ResponseEntity<FixReportResponse> getFixReports(@PathVariable("sprintId") long sprintId) {
        return ResponseEntity.status(OK).body(fixReportFacade.getFixReports(sprintId));
    }

    @GetMapping("/{part}")
    public ResponseEntity<byte[]> downloadFixReport(
            @PathVariable("sprintId") long sprintId,
            @PathVariable("part") ProgressPart part
    ) {
        FixReportFileDto file = fixReportFacade.getFixReportFile(sprintId, part);
        return ResponseEntity.status(OK)
                .contentType(TEXT_PLAIN_UTF_8)
                .header(HttpHeaders.CONTENT_DISPOSITION, FixReportFileName.contentDisposition(file.fileName()))
                .body(file.content().getBytes(StandardCharsets.UTF_8));
    }
}
