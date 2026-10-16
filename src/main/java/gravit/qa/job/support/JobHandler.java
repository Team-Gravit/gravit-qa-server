package gravit.qa.job.support;

import gravit.qa.job.domain.JobType;

public interface JobHandler {

    JobType type();

    void handle(JobContext context);
}
