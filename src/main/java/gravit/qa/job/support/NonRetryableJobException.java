package gravit.qa.job.support;

public class NonRetryableJobException extends RuntimeException {

    public NonRetryableJobException(String message) {
        super(message);
    }

    public NonRetryableJobException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
