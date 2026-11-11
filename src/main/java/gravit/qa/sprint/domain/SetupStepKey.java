package gravit.qa.sprint.domain;

import gravit.qa.job.domain.JobType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SetupStepKey {

    CONNECT(JobType.SPEC_LOAD, SprintStatus.SPEC_LOAD),
    SPEC_COLLECT(JobType.SPEC_LOAD, SprintStatus.SPEC_LOAD),
    CHECKLIST_GENERATE(JobType.QA_PREPARE, SprintStatus.CHECKLIST),
    DESIGN_REVIEW(JobType.QA_PREPARE, SprintStatus.DESIGN_REVIEW),
    AUTO_REVIEW(JobType.QA_PREPARE, SprintStatus.AUTO_REVIEW);

    private final JobType jobType;
    private final SprintStatus sprintStatus;
}
