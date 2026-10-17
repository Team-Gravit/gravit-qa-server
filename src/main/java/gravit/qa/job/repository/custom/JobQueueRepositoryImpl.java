package gravit.qa.job.repository.custom;

import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobStepStatus;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.dto.internal.ClaimedJobDto;
import gravit.qa.job.dto.internal.JobDto;
import gravit.qa.job.dto.internal.JobStepDto;
import gravit.qa.job.dto.internal.ReapedJobDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static gravit.qa.job.repository.sql.JobQueueSql.CLAIM_NEXT;
import static gravit.qa.job.repository.sql.JobQueueSql.FAIL_RUNNING_STEPS;
import static gravit.qa.job.repository.sql.JobQueueSql.FINISH_STEP;
import static gravit.qa.job.repository.sql.JobQueueSql.HEARTBEAT;
import static gravit.qa.job.repository.sql.JobQueueSql.INSERT_JOB;
import static gravit.qa.job.repository.sql.JobQueueSql.INSERT_STEP;
import static gravit.qa.job.repository.sql.JobQueueSql.MARK_CANCELLED;
import static gravit.qa.job.repository.sql.JobQueueSql.MARK_FAILED;
import static gravit.qa.job.repository.sql.JobQueueSql.MARK_SUCCEEDED;
import static gravit.qa.job.repository.sql.JobQueueSql.REAP_STALE;
import static gravit.qa.job.repository.sql.JobQueueSql.REQUEST_CANCEL;
import static gravit.qa.job.repository.sql.JobQueueSql.RESET_STEPS;
import static gravit.qa.job.repository.sql.JobQueueSql.SELECT_CANCEL_REQUESTED;
import static gravit.qa.job.repository.sql.JobQueueSql.SELECT_JOB;
import static gravit.qa.job.repository.sql.JobQueueSql.SELECT_LATEST_JOB_ID;
import static gravit.qa.job.repository.sql.JobQueueSql.SELECT_SPRINT_IDS_WITH_ACTIVE_JOB;
import static gravit.qa.job.repository.sql.JobQueueSql.SELECT_STEPS;
import static gravit.qa.job.repository.sql.JobQueueSql.START_STEP;

@Repository
@RequiredArgsConstructor
public class JobQueueRepositoryImpl implements JobQueueRepository {

    private static final RowMapper<JobDto> JOB_ROW_MAPPER = (rs, rowNum) -> new JobDto(
            rs.getLong("id"),
            JobType.valueOf(rs.getString("type")),
            nullableLong(rs, "sprint_id"),
            JobStatus.valueOf(rs.getString("status")),
            rs.getInt("attempt"),
            rs.getInt("max_attempts"),
            rs.getObject("run_after", LocalDateTime.class),
            rs.getBoolean("cancel_requested"),
            rs.getString("last_error"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("finished_at", LocalDateTime.class)
    );

    private static final RowMapper<JobStepDto> STEP_ROW_MAPPER = (rs, rowNum) -> new JobStepDto(
            rs.getInt("seq"),
            rs.getString("name"),
            JobStepStatus.valueOf(rs.getString("status")),
            rs.getString("message"),
            rs.getObject("started_at", LocalDateTime.class),
            rs.getObject("finished_at", LocalDateTime.class)
    );

    private static final RowMapper<ReapedJobDto> REAPED_ROW_MAPPER = (rs, rowNum) -> new ReapedJobDto(
            rs.getLong("id"),
            JobType.valueOf(rs.getString("type")),
            nullableLong(rs, "sprint_id"),
            JobStatus.valueOf(rs.getString("status"))
    );

    private static final RowMapper<ClaimedJobDto> CLAIMED_ROW_MAPPER = (rs, rowNum) -> new ClaimedJobDto(
            rs.getLong("id"),
            JobType.valueOf(rs.getString("type")),
            nullableLong(rs, "sprint_id"),
            rs.getString("payload"),
            rs.getInt("attempt"),
            rs.getInt("max_attempts")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public long insertJob(
            JobType type,
            Long sprintId,
            String payload,
            int maxAttempts,
            LocalDateTime now
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("type", type.name())
                .addValue("sprintId", sprintId)
                .addValue("payload", payload)
                .addValue("maxAttempts", maxAttempts)
                .addValue("now", now);

        Long id = jdbcTemplate.queryForObject(INSERT_JOB, params, Long.class);
        if (id == null) {
            throw new IllegalStateException("작업 ID를 받지 못했습니다.");
        }
        return id;
    }

    @Override
    public void insertSteps(
            long jobId,
            List<String> stepNames
    ) {
        MapSqlParameterSource[] batch = IntStream.range(0, stepNames.size())
                .mapToObj(index -> new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("seq", index + 1)
                        .addValue("name", stepNames.get(index)))
                .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(INSERT_STEP, batch);
    }

    @Override
    public Optional<JobDto> findJob(long jobId) {
        return jdbcTemplate.query(SELECT_JOB, jobIdParam(jobId), JOB_ROW_MAPPER).stream().findFirst();
    }

    @Override
    public List<JobStepDto> findSteps(long jobId) {
        return jdbcTemplate.query(SELECT_STEPS, jobIdParam(jobId), STEP_ROW_MAPPER);
    }

    @Override
    public Optional<ClaimedJobDto> claimNext(
            Collection<JobType> types,
            String workerId,
            LocalDateTime now
    ) {
        if (types.isEmpty()) {
            return Optional.empty();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("types", types.stream().map(JobType::name).toList())
                .addValue("workerId", workerId)
                .addValue("now", now);

        return jdbcTemplate.query(CLAIM_NEXT, params, CLAIMED_ROW_MAPPER).stream().findFirst();
    }

    @Override
    public void resetSteps(long jobId) {
        jdbcTemplate.update(RESET_STEPS, jobIdParam(jobId));
    }

    @Override
    public void startStep(
            long jobId,
            int seq,
            LocalDateTime now
    ) {
        MapSqlParameterSource params = jobIdParam(jobId)
                .addValue("seq", seq)
                .addValue("now", now);

        jdbcTemplate.update(START_STEP, params);
    }

    @Override
    public void finishStep(
            long jobId,
            int seq,
            JobStepStatus status,
            String message,
            LocalDateTime now
    ) {
        MapSqlParameterSource params = jobIdParam(jobId)
                .addValue("seq", seq)
                .addValue("status", status.name())
                .addValue("message", message)
                .addValue("now", now);

        jdbcTemplate.update(FINISH_STEP, params);
    }

    @Override
    public boolean isCancelRequested(long jobId) {
        Boolean requested = jdbcTemplate.queryForObject(SELECT_CANCEL_REQUESTED, jobIdParam(jobId), Boolean.class);
        return Boolean.TRUE.equals(requested);
    }

    @Override
    public int heartbeat(
            String workerId,
            LocalDateTime now
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workerId", workerId)
                .addValue("now", now);

        return jdbcTemplate.update(HEARTBEAT, params);
    }

    @Override
    public boolean markSucceeded(
            long jobId,
            String workerId,
            LocalDateTime now
    ) {
        return jdbcTemplate.update(MARK_SUCCEEDED, leaseParams(jobId, workerId, now)) == 1;
    }

    @Override
    public boolean markCancelled(
            long jobId,
            String workerId,
            LocalDateTime now
    ) {
        return jdbcTemplate.update(MARK_CANCELLED, leaseParams(jobId, workerId, now)) == 1;
    }

    @Override
    public Optional<JobStatus> markFailed(
            long jobId,
            String workerId,
            boolean retryable,
            String error,
            LocalDateTime retryAt,
            LocalDateTime now
    ) {
        MapSqlParameterSource params = leaseParams(jobId, workerId, now)
                .addValue("retryable", retryable)
                .addValue("error", error)
                .addValue("retryAt", retryAt);

        return jdbcTemplate.query(MARK_FAILED, params, (rs, rowNum) -> JobStatus.valueOf(rs.getString("status")))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<JobStatus> requestCancel(
            long jobId,
            LocalDateTime now
    ) {
        MapSqlParameterSource params = jobIdParam(jobId).addValue("now", now);

        return jdbcTemplate.query(REQUEST_CANCEL, params, (rs, rowNum) -> JobStatus.valueOf(rs.getString("status")))
                .stream()
                .findFirst();
    }

    @Override
    public List<ReapedJobDto> reapStale(
            LocalDateTime staleBefore,
            String error,
            LocalDateTime now
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("staleBefore", staleBefore)
                .addValue("error", error)
                .addValue("now", now);

        List<ReapedJobDto> reaped = jdbcTemplate.query(REAP_STALE, params, REAPED_ROW_MAPPER);
        if (!reaped.isEmpty()) {
            MapSqlParameterSource stepParams = new MapSqlParameterSource()
                    .addValue("jobIds", reaped.stream().map(ReapedJobDto::id).toList())
                    .addValue("message", error)
                    .addValue("now", now);
            jdbcTemplate.update(FAIL_RUNNING_STEPS, stepParams);
        }
        return reaped;
    }

    @Override
    public Optional<Long> findLatestJobId(
            long sprintId,
            JobType type
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sprintId", sprintId)
                .addValue("type", type.name());

        return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_LATEST_JOB_ID, params, Long.class));
    }

    @Override
    public Set<Long> findSprintIdsWithActiveJob(
            Collection<Long> sprintIds,
            JobType type
    ) {
        if (sprintIds.isEmpty()) {
            return Set.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sprintIds", sprintIds)
                .addValue("type", type.name());

        return new HashSet<>(jdbcTemplate.queryForList(SELECT_SPRINT_IDS_WITH_ACTIVE_JOB, params, Long.class));
    }

    private MapSqlParameterSource jobIdParam(long jobId) {
        return new MapSqlParameterSource().addValue("jobId", jobId);
    }

    private MapSqlParameterSource leaseParams(
            long jobId,
            String workerId,
            LocalDateTime now
    ) {
        return jobIdParam(jobId)
                .addValue("workerId", workerId)
                .addValue("now", now);
    }

    private static Long nullableLong(
            ResultSet rs,
            String column
    ) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
