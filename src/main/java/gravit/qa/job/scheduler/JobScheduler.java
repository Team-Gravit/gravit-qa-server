package gravit.qa.job.scheduler;

import gravit.qa.job.support.JobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "qa.worker.enabled", havingValue = "true", matchIfMissing = true)
public class JobScheduler {

    private final JobRunner jobRunner;

    @Scheduled(fixedDelayString = "${qa.worker.poll-interval-ms:5000}")
    public void poll() {
        boolean ran;
        do {
            ran = jobRunner.runNext();
        } while (ran);
    }

    @Scheduled(fixedDelayString = "${qa.worker.heartbeat-interval-ms:30000}")
    public void heartbeat() {
        jobRunner.heartbeat();
    }

    @Scheduled(fixedDelayString = "${qa.job.reaper-interval-ms:60000}")
    public void reapStale() {
        jobRunner.reapStale();
    }
}
