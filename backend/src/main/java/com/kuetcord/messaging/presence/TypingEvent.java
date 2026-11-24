package com.kuetcord.messaging.presence;

import java.time.Instant;

/**
 * Broadcast on {@code /topic/conversations/{id}/typing}. The client resolves {@code userId} to a name
 * from the member list it already holds, and clears the indicator 4 s after the last event.
 */
public record TypingEvent(Long conversationId, Long userId, Instant timestamp) {
}
