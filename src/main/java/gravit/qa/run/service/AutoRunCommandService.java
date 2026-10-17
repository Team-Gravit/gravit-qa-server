package gravit.qa.run.service;

import gravit.qa.global.util.KstTimes;
import gravit.qa.run.domain.AutoRun;
import gravit.qa.run.dto.response.AutoRunSummaryResponse;
import gravit.qa.run.repository.AutoRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AutoRunCommandService {

    private final AutoRunRepository autoRunRepository;

    private final Clock clock;

    @Transactional
    public AutoRunSummaryResponse create(
            long sprintId,
            long baselineId,
            long jobId
    ) {
        AutoRun autoRun = autoRunRepository.save(AutoRun.create(sprintId, baselineId, jobId, KstTimes.now(clock)));
        return AutoRunSummaryResponse.from(autoRun);
    }
}
