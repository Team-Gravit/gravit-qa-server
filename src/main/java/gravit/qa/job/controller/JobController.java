package gravit.qa.job.controller;

import gravit.qa.job.controller.docs.JobControllerDocs;
import gravit.qa.job.dto.response.JobResponse;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.job.service.JobQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/jobs")
public class JobController implements JobControllerDocs {

    private final JobCommandService jobCommandService;
    private final JobQueryService jobQueryService;

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(@PathVariable("jobId") long jobId) {
        return ResponseEntity.status(OK).body(jobQueryService.getJob(jobId));
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<JobResponse> cancelJob(@PathVariable("jobId") long jobId) {
        return ResponseEntity.status(ACCEPTED).body(jobCommandService.requestCancel(jobId));
    }
}
