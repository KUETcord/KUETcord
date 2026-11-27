package com.kuetcord.notifications.outbox;

import com.kuetcord.notifications.dto.NotificationDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inserts all notifications of one outbox event in a single round trip ({@code unnest} of parallel
 * arrays), however many recipients there are.
 * <p>
 * {@code ON CONFLICT DO NOTHING} on {@code uq_notification_dedup (recipient_id, source_event_id, type)}
 * makes replays harmless, and {@code RETURNING} yields only rows that are new, so a replayed event is
 * never pushed twice. Recipients whose account no longer exists are skipped instead of failing the
 * whole batch on the foreign key.
 */
@Repository
public class NotificationWriter {

    private static final String INSERT_SQL = """
            INSERT INTO notifications (recipient_id, type, title, preview, target_type, target_id, source_event_id)
            SELECT t.recipient_id, t.type, t.title, t.preview, t.target_type, t.target_id, ?
              FROM unnest(?::bigint[], ?::varchar[], ?::varchar[], ?::varchar[], ?::varchar[], ?::bigint[])
                   AS t(recipient_id, type, title, preview, target_type, target_id)
             WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = t.recipient_id)
            ON CONFLICT ON CONSTRAINT uq_notification_dedup DO NOTHING
            RETURNING id, recipient_id, type, title, preview, target_type, target_id, created_at
            """;

    /** A freshly inserted row and who should receive the live push. */
    public record Inserted(Long recipientId, NotificationDto notification) {
    }

    private final JdbcTemplate jdbcTemplate;

    public NotificationWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Inserted> insertAll(List<NotificationDraft> drafts, String sourceEventId) {
        // One notification per (recipient, type) per event; the last draft wins.
        Map<String, NotificationDraft> unique = new LinkedHashMap<>();
        drafts.forEach(draft -> unique.put(draft.recipientId() + "|" + draft.type(), draft));
        if (unique.isEmpty()) {
            return List.of();
        }
        List<NotificationDraft> rows = List.copyOf(unique.values());

        return jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT_SQL);
            statement.setString(1, sourceEventId);
            statement.setArray(2, connection.createArrayOf("bigint", rows.stream().map(NotificationDraft::recipientId).toArray()));
            statement.setArray(3, connection.createArrayOf("varchar", rows.stream().map(NotificationDraft::type).toArray()));
            statement.setArray(4, connection.createArrayOf("varchar", rows.stream().map(NotificationDraft::title).toArray()));
            statement.setArray(5, connection.createArrayOf("varchar", rows.stream().map(NotificationDraft::preview).toArray()));
            statement.setArray(6, connection.createArrayOf("varchar", rows.stream().map(NotificationDraft::targetType).toArray()));
            statement.setArray(7, connection.createArrayOf("bigint", rows.stream().map(NotificationDraft::targetId).toArray()));
            return statement;
        }, (rs, rowNum) -> new Inserted(rs.getLong("recipient_id"), new NotificationDto(
                rs.getLong("id"),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("preview"),
                rs.getString("target_type"),
                rs.getLong("target_id"),
                rs.getTimestamp("created_at").toInstant(),
                null,
                false)));
    }
}
