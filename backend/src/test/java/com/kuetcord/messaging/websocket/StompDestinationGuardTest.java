package com.kuetcord.messaging.websocket;

import com.kuetcord.messaging.access.ConversationAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Security unit tests for STOMP SUBSCRIBE/SEND authorization and per-session frame rate limiting.
 */
class StompDestinationGuardTest {

    private static final long MEMBER = 1L;
    private static final long STRANGER = 2L;
    private static final long CONVERSATION = 42L;

    private StompDestinationGuard guard;

    @BeforeEach
    void setUp() {
        ConversationAccessPolicy policy = mock(ConversationAccessPolicy.class);
        when(policy.canAccess(CONVERSATION, MEMBER)).thenReturn(true);
        when(policy.canSend(CONVERSATION, MEMBER)).thenReturn(true);
        guard = new StompDestinationGuard(policy);
    }

    @Test
    @DisplayName("Subscribing to a conversation topic without membership throws AccessDeniedException")
    void shouldPreventUnauthorizedChannelSubscription() {
        assertDoesNotThrow(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, MEMBER, "/topic/conversations/42", "s1"), null));
        assertDoesNotThrow(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, MEMBER, "/topic/conversations/42/typing", "s1"), null));
        assertThrows(AccessDeniedException.class,
                () -> guard.preSend(frame(StompCommand.SUBSCRIBE, STRANGER, "/topic/conversations/42", "s2"), null));
    }

    @Test
    @DisplayName("Fail-closed: raw /queue snooping, direct /topic SENDs and unauthenticated frames are denied")
    void shouldDenyEverythingOutsideTheAllowlist() {
        assertDoesNotThrow(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, STRANGER, "/topic/presence", "s1"), null));
        assertDoesNotThrow(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, STRANGER, "/user/queue/notifications", "s1"), null));

        assertThrows(AccessDeniedException.class,
                () -> guard.preSend(frame(StompCommand.SUBSCRIBE, MEMBER, "/queue/notifications-usersession123", "s1"), null));
        assertThrows(AccessDeniedException.class,
                () -> guard.preSend(frame(StompCommand.SUBSCRIBE, MEMBER, "/topic/everything", "s1"), null));
        assertThrows(AccessDeniedException.class,
                () -> guard.preSend(frame(StompCommand.SEND, MEMBER, "/topic/conversations/42", "s1"), null),
                "Clients must not publish straight to the broker and bypass persistence");
        assertThrows(AccessDeniedException.class,
                () -> guard.preSend(frame(StompCommand.SEND, STRANGER, "/app/conversations/42/messages", "s2"), null));
        assertThrows(AccessDeniedException.class,
                () -> guard.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/presence", "s3"), null));
    }

    @Test
    @DisplayName("More than 5 SEND frames per second on one session is rejected; DISCONNECT frees the bucket")
    void shouldRateLimitSendFramesPerSession() {
        for (int i = 0; i < StompDestinationGuard.MAX_FRAMES_PER_SECOND; i++) {
            assertDoesNotThrow(() -> guard.preSend(frame(StompCommand.SEND, MEMBER, "/app/conversations/42/messages", "burst"), null));
        }
        assertThrows(AccessDeniedException.class,
                () -> guard.preSend(frame(StompCommand.SEND, MEMBER, "/app/conversations/42/messages", "burst"), null));
        // A different session (another tab) has its own budget.
        assertDoesNotThrow(() -> guard.preSend(frame(StompCommand.SEND, MEMBER, "/app/conversations/42/messages", "other-tab"), null));

        assertEquals(2, guard.trackedSessions());
        guard.preSend(frame(StompCommand.DISCONNECT, MEMBER, null, "burst"), null);
        assertEquals(1, guard.trackedSessions());
    }

    private static Message<byte[]> frame(StompCommand command, Long userId, String destination, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setSessionId(sessionId);
        if (userId != null) {
            accessor.setUser(new StompPrincipal(userId, "u" + userId + "@stud.kuet.ac.bd"));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
