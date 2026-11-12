package gravit.qa.sprint.handler;

import gravit.qa.global.util.KstTimes;
import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.dto.event.JobTerminatedEvent;
import gravit.qa.sprint.service.SetupStepService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class PrepareJobTerminatedListener {

    private static final String CANCELLED_MESSAGE = "작업이 취소되었습니다";
    private static final String STOPPED_MESSAGE = "작업이 멈췄습니다. 다시 시작하면 이 단계부터 이어서 합니다";

    private final SetupStepService setupStepService;

    private final Clock clock;

    @EventListener
    public void onTerminated(JobTerminatedEvent event) {
        if (event.sprintId() == null) {
            return;
        }
        String message = event.status() == JobStatus.CANCELLED ? CANCELLED_MESSAGE : STOPPED_MESSAGE;
        setupStepService.failRunning(event.sprintId(), event.type(), message, KstTimes.now(clock));
    }
}
