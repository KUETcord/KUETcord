package com.kuetcord.messaging.presence;

import java.time.Instant;

/**
 * Broadcast on {@code /topic/presence} and returned by {@code GET /api/v1/users/presence}.
 * Clients keep the newest {@code timestamp} per user and ignore older events.
 */
public record PresenceEvent(Long userId, PresenceStatus status, Instant timestamp) {
}
