package gravit.qa.job.support;

public class JobCancelledException extends RuntimeException {

    public JobCancelledException(long jobId) {
        super("작업이 취소되었습니다. jobId=" + jobId);
    }
}
