package com.kuetcord.notifications.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Lease-based claiming of {@code outbox_events} for the notification worker.
 * <p>
 * The claim is one statement: the CTE {@code SELECT ... FOR UPDATE SKIP LOCKED} picks up to N
 * claimable rows that no other worker holds (a locking CTE is evaluated exactly once), and the
 * {@code UPDATE} leases them
 * ({@code status = 'PROCESSING'}, {@code next_attempt_at = now() + lease}). Row locks are released
 * as soon as the claim commits, so processing never holds a lock across the work itself.
 * A worker that dies mid-batch leaves its rows {@code PROCESSING}; once the lease expires they become
 * claimable again. Duplicate inserts after such a re-claim are absorbed by {@code uq_notification_dedup}.
 */
@Repository
public class OutboxClaimRepository {

    private static final String CLAIM_SQL = """
            WITH picked AS (
                SELECT id FROM outbox_events
                 WHERE event_type = ANY (?)
                   AND ((status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= NOW()))
                     OR (status = 'PROCESSING' AND next_attempt_at <= NOW()))
                 ORDER BY id
                 LIMIT ?
                   FOR UPDATE SKIP LOCKED)
            UPDATE outbox_events o
               SET status = 'PROCESSING',
                   next_attempt_at = NOW() + (? * INTERVAL '1 second')
              FROM picked
             WHERE o.id = picked.id
            RETURNING o.id, o.event_type, o.aggregate_id, o.payload::text AS payload, o.occurred_at, o.retry_count
            """;

    private static final String MARK_PROCESSED_SQL = """
            UPDATE outbox_events
               SET status = 'PROCESSED', processed_at = NOW(), next_attempt_at = NULL, last_error_code = NULL
             WHERE id = ? AND status = 'PROCESSING'
            """;

    /** Exponential backoff 2^attempt seconds, capped at 5 minutes. */
    private static final String MARK_FAILED_SQL = """
            UPDATE outbox_events
               SET retry_count = ?,
                   status = CASE WHEN ? >= ? THEN 'DEAD_LETTER' ELSE 'PENDING' END,
                   next_attempt_at = NOW() + (LEAST(300, POWER(2, ?)) * INTERVAL '1 second'),
                   last_error_code = ?
             WHERE id = ? AND status = 'PROCESSING'
            """;

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxClaimRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<ClaimedOutboxEvent> claim(Collection<String> eventTypes, int limit, int leaseSeconds) {
        if (eventTypes.isEmpty()) {
            return List.of();
        }
        List<ClaimedOutboxEvent> claimed = jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(CLAIM_SQL);
            Array types = connection.createArrayOf("varchar", eventTypes.toArray());
            statement.setArray(1, types);
            statement.setInt(2, limit);
            statement.setInt(3, leaseSeconds);
            return statement;
        }, (rs, rowNum) -> map(rs));
        // RETURNING order is unspecified; process oldest first.
        return claimed.stream().sorted(Comparator.comparing(ClaimedOutboxEvent::id)).toList();
    }

    /** @return false when the lease was lost (another worker re-claimed and finished the row). */
    public boolean markProcessed(Long eventId) {
        return jdbcTemplate.update(MARK_PROCESSED_SQL, eventId) == 1;
    }

    public void markFailed(Long eventId, int attempts, int maxAttempts, String errorCode) {
        jdbcTemplate.update(MARK_FAILED_SQL, attempts, attempts, maxAttempts, attempts, errorCode, eventId);
    }

    private ClaimedOutboxEvent map(ResultSet rs) throws SQLException {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rs.getString("payload"), PAYLOAD_TYPE);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new ClaimedOutboxEvent(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getString("aggregate_id"),
                payload,
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getInt("retry_count"));
    }
}
