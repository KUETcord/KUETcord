package com.kuetcord.messaging.presence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypingRegistryTest {

    @Test
    @DisplayName("Typing pings are throttled per user (2 s), independent across users, and pruned when stale")
    void shouldThrottleTypingPingsPerUser() {
        AtomicLong now = new AtomicLong(1_000_000);
        TypingRegistry registry = new TypingRegistry(now::get);

        assertTrue(registry.recordTyping(7L, 1L));
        assertFalse(registry.recordTyping(7L, 1L), "Second ping inside the window is dropped");
        assertTrue(registry.recordTyping(7L, 2L), "Another user in the same conversation is independent");

        now.addAndGet(TypingRegistry.THROTTLE_MS);
        assertTrue(registry.recordTyping(7L, 1L));

        registry.clear(7L, 1L);
        assertTrue(registry.recordTyping(7L, 1L), "Sending a message resets the throttle");

        now.addAndGet(TypingRegistry.STALE_AFTER_MS + 1);
        registry.recordTyping(8L, 3L);
        registry.clear(7L, 1L);
        registry.clear(7L, 2L);
        assertEquals(1, registry.trackedConversations(), "Idle conversations do not accumulate");
    }
}
