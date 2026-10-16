package gravit.qa.job.repository.sql;

import lombok.experimental.UtilityClass;

@UtilityClass
public class JobQueueSql {

    public static final String INSERT_JOB = """
            INSERT INTO job (type, sprint_id, status, payload, attempt, max_attempts, run_after, created_at)
            VALUES (:type, :sprintId, 'QUEUED', CAST(:payload AS JSONB), 0, :maxAttempts, :now, :now)
            RETURNING id
            """;

    public static final String INSERT_STEP = """
            INSERT INTO job_step (job_id, seq, name, status)
            VALUES (:jobId, :seq, :name, 'PENDING')
            """;

    public static final String SELECT_JOB = """
            SELECT id, type, sprint_id, status, attempt, max_attempts, run_after,
                   cancel_requested, last_error, created_at, finished_at
            FROM job
            WHERE id = :jobId
            """;

    public static final String SELECT_STEPS = """
            SELECT seq, name, status, message, started_at, finished_at
            FROM job_step
            WHERE job_id = :jobId
            ORDER BY seq
            """;

    public static final String CLAIM_NEXT = """
            UPDATE job
            SET status = 'RUNNING',
                attempt = attempt + 1,
                locked_by = :workerId,
                locked_at = :now,
                heartbeat_at = :now
            WHERE id = (
                SELECT id
                FROM job
                WHERE status = 'QUEUED' AND type IN (:types) AND run_after <= :now
                ORDER BY id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            RETURNING id, type, sprint_id, payload::TEXT AS payload, attempt, max_attempts
            """;

    public static final String RESET_STEPS = """
            UPDATE job_step
            SET status = 'PENDING', message = NULL, started_at = NULL, finished_at = NULL
            WHERE job_id = :jobId
            """;

    public static final String START_STEP = """
            UPDATE job_step
            SET status = 'RUNNING', message = NULL, started_at = :now, finished_at = NULL
            WHERE job_id = :jobId AND seq = :seq
            """;

    public static final String FINISH_STEP = """
            UPDATE job_step
            SET status = :status, message = :message, finished_at = :now
            WHERE job_id = :jobId AND seq = :seq
            """;

    public static final String FAIL_RUNNING_STEPS = """
            UPDATE job_step
            SET status = 'FAILED', message = :message, finished_at = :now
            WHERE job_id IN (:jobIds) AND status = 'RUNNING'
            """;

    public static final String SELECT_CANCEL_REQUESTED = """
            SELECT cancel_requested
            FROM job
            WHERE id = :jobId
            """;

    public static final String HEARTBEAT = """
            UPDATE job
            SET heartbeat_at = :now
            WHERE status = 'RUNNING' AND locked_by = :workerId
            """;

    public static final String MARK_SUCCEEDED = """
            UPDATE job
            SET status = 'SUCCEEDED', finished_at = :now,
                locked_by = NULL, locked_at = NULL, heartbeat_at = NULL
            WHERE id = :jobId AND status = 'RUNNING' AND locked_by = :workerId
            """;

    public static final String MARK_CANCELLED = """
            UPDATE job
            SET status = 'CANCELLED', finished_at = :now,
                locked_by = NULL, locked_at = NULL, heartbeat_at = NULL
            WHERE id = :jobId AND status = 'RUNNING' AND locked_by = :workerId
            """;

    public static final String MARK_FAILED = """
            UPDATE job
            SET status = CASE WHEN CAST(:retryable AS BOOLEAN) AND attempt < max_attempts THEN 'QUEUED' ELSE 'FAILED' END,
                run_after = CASE WHEN CAST(:retryable AS BOOLEAN) AND attempt < max_attempts THEN CAST(:retryAt AS TIMESTAMP) ELSE run_after END,
                finished_at = CASE WHEN CAST(:retryable AS BOOLEAN) AND attempt < max_attempts THEN NULL ELSE CAST(:now AS TIMESTAMP) END,
                last_error = :error,
                locked_by = NULL, locked_at = NULL, heartbeat_at = NULL
            WHERE id = :jobId AND status = 'RUNNING' AND locked_by = :workerId
            RETURNING status
            """;

    public static final String REQUEST_CANCEL = """
            UPDATE job
            SET finished_at = CASE WHEN status = 'QUEUED' THEN CAST(:now AS TIMESTAMP) ELSE finished_at END,
                status = CASE WHEN status = 'QUEUED' THEN 'CANCELLED' ELSE status END,
                cancel_requested = TRUE
            WHERE id = :jobId AND status IN ('QUEUED', 'RUNNING')
            RETURNING status
            """;

    public static final String REAP_STALE = """
            UPDATE job
            SET status = CASE
                    WHEN cancel_requested THEN 'CANCELLED'
                    WHEN attempt < max_attempts THEN 'QUEUED'
                    ELSE 'FAILED'
                END,
                run_after = CAST(:now AS TIMESTAMP),
                finished_at = CASE WHEN NOT cancel_requested AND attempt < max_attempts THEN NULL ELSE CAST(:now AS TIMESTAMP) END,
                last_error = :error,
                locked_by = NULL, locked_at = NULL, heartbeat_at = NULL
            WHERE status = 'RUNNING' AND heartbeat_at < :staleBefore
            RETURNING id
            """;
}
