package com.kuetcord.messaging.presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for multi-tab, reference-counted presence (no Spring context, no sockets).
 */
class SimpUserRegistryPresenceTest {

    private static final long USER = 101L;

    private SimpMessageSendingOperations template;
    private PresenceService presence;

    @BeforeEach
    void setUp() {
        template = mock(SimpMessageSendingOperations.class);
        presence = new PresenceService(template, Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Two tabs: closing one keeps the user ONLINE; closing both goes OFFLINE; only transitions broadcast")
    void shouldMaintainOnlineStatusWhenOneOfTwoTabsClosed() {
        presence.sessionConnected(USER, "tab-1");
        presence.sessionConnected(USER, "tab-2");
        assertEquals(PresenceStatus.ONLINE, presence.statusOf(USER));
        assertEquals(2, presence.sessionCount(USER));

        presence.sessionDisconnected(USER, "tab-1");
        assertEquals(PresenceStatus.ONLINE, presence.statusOf(USER), "Still one tab open");

        presence.sessionDisconnected(USER, "tab-2");
        assertEquals(PresenceStatus.OFFLINE, presence.statusOf(USER));

        assertEquals(List.of(PresenceStatus.ONLINE, PresenceStatus.OFFLINE), broadcastStatuses(2));
    }

    @Test
    @DisplayName("Duplicate disconnect events for the same session cannot knock out the remaining tab")
    void shouldBeIdempotentForDuplicateDisconnectEvents() {
        presence.sessionConnected(USER, "tab-1");
        presence.sessionConnected(USER, "tab-2");

        presence.sessionDisconnected(USER, "tab-1");
        presence.sessionDisconnected(USER, "tab-1"); // Spring may emit SessionDisconnectEvent twice

        assertEquals(PresenceStatus.ONLINE, presence.statusOf(USER), "A bare counter would have reached 0 here");
        assertEquals(1, presence.sessionCount(USER));
    }

    @Test
    @DisplayName("IDLE only when every tab is idle; any active tab makes the user ONLINE again")
    void shouldReportIdleOnlyWhenAllSessionsIdle() {
        presence.sessionConnected(USER, "tab-1");
        presence.sessionConnected(USER, "tab-2");

        presence.setIdle(USER, "tab-1", true);
        assertEquals(PresenceStatus.ONLINE, presence.statusOf(USER));
        presence.setIdle(USER, "tab-2", true);
        assertEquals(PresenceStatus.IDLE, presence.statusOf(USER));
        presence.setIdle(USER, "tab-2", false);
        assertEquals(PresenceStatus.ONLINE, presence.statusOf(USER));

        presence.setIdle(USER, "unknown-session", true);
        assertEquals(PresenceStatus.ONLINE, presence.statusOf(USER), "Unknown sessions are ignored");
        assertEquals(Map.of(USER, PresenceStatus.ONLINE, 999L, PresenceStatus.OFFLINE), presence.statusOf(List.of(USER, 999L)));
    }

    private List<PresenceStatus> broadcastStatuses(int expected) {
        ArgumentCaptor<PresenceEvent> captor = ArgumentCaptor.forClass(PresenceEvent.class);
        verify(template, times(expected)).convertAndSend(eq(PresenceService.PRESENCE_TOPIC), captor.capture());
        return captor.getAllValues().stream().map(PresenceEvent::status).toList();
    }
}
