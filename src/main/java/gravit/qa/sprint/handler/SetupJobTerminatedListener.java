package gravit.qa.sprint.handler;

import gravit.qa.adapter.exception.ReleaseBranchNotFoundException;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.dto.event.JobTerminatedEvent;
import gravit.qa.sprint.domain.PrepareFailureReason;
import gravit.qa.sprint.service.SprintCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SetupJobTerminatedListener {

    private final SprintCommandService sprintCommandService;

    @EventListener
    public void onTerminated(JobTerminatedEvent event) {
        if (event.type() != JobType.SETUP || event.sprintId() == null) {
            return;
        }
        sprintCommandService.markPrepareFailed(event.sprintId(), reasonOf(event.cause()));
    }

    private PrepareFailureReason reasonOf(Throwable cause) {
        if (cause instanceof ReleaseBranchNotFoundException) {
            return PrepareFailureReason.RELEASE_BRANCH_MISSING;
        }
        return null;
    }
}
