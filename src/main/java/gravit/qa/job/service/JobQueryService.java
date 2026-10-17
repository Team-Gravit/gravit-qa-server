package gravit.qa.job.service;

import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.dto.internal.JobDto;
import gravit.qa.job.dto.response.JobResponse;
import gravit.qa.job.repository.custom.JobQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static gravit.qa.global.exception.domain.CustomErrorCode.JOB_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class JobQueryService {

    private final JobQueueRepository jobQueueRepository;

    @Transactional(readOnly = true)
    public JobResponse getJob(long jobId) {
        JobDto job = jobQueueRepository.findJob(jobId)
                .orElseThrow(() -> new RestApiException(JOB_NOT_FOUND));

        return JobResponse.of(job, jobQueueRepository.findSteps(jobId));
    }

    @Transactional(readOnly = true)
    public Optional<Long> findLatestJobId(
            long sprintId,
            JobType type
    ) {
        return jobQueueRepository.findLatestJobId(sprintId, type);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveJob(
            long sprintId,
            JobType type
    ) {
        return jobQueueRepository.findSprintIdsWithActiveJob(Set.of(sprintId), type).contains(sprintId);
    }

    @Transactional(readOnly = true)
    public Set<Long> findSprintIdsWithActiveJob(
            Collection<Long> sprintIds,
            JobType type
    ) {
        return jobQueueRepository.findSprintIdsWithActiveJob(sprintIds, type);
    }
}
