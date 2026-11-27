package com.kuetcord.common.outbox;

/**
 * Lifecycle states of a transactional outbox record in {@code outbox_events}.
 */
public enum OutboxStatus {
    PENDING,
    /** Leased by a worker; reclaimable once {@code next_attempt_at} (the lease end) has passed. */
    PROCESSING,
    PROCESSED,
    DEAD_LETTER
}
