package com.kuetcord.notifications.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** An outbox row leased by this worker (status {@code PROCESSING}). */
public record ClaimedOutboxEvent(
        Long id,
        String eventType,
        String aggregateId,
        Map<String, Object> payload,
        Instant occurredAt,
        int retryCount) {

    public String sourceEventId() {
        return "outbox:" + id;
    }

    public Long requireLong(String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.valueOf(text);
        }
        throw new IllegalArgumentException("Outbox payload field '" + key + "' is missing or not a number");
    }

    public Long optionalLong(String key) {
        Object value = payload.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    public String text(String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    public List<Long> requireLongList(String key) {
        if (!(payload.get(key) instanceof List<?> values)) {
            throw new IllegalArgumentException("Outbox payload field '" + key + "' is missing or not a list");
        }
        return values.stream()
                .filter(Number.class::isInstance)
                .map(value -> ((Number) value).longValue())
                .toList();
    }

    public List<Long> optionalLongList(String key) {
        return payload.get(key) instanceof List<?> ? requireLongList(key) : List.of();
    }
}
