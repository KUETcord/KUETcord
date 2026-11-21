package com.kuetcord.common.outbox;

/**
 * Lifecycle states of a transactional outbox record in {@code outbox_events}.
 */
public enum OutboxStatus {
    PENDING,
    PROCESSED,
    DEAD_LETTER
}
