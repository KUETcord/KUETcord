package com.kuetcord.messaging.websocket;

import com.kuetcord.messaging.presence.PresenceService;
import com.kuetcord.messaging.presence.TypingEvent;
import com.kuetcord.messaging.presence.TypingRegistry;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

/**
 * Ephemeral real-time signals: typing pings and tab idle/active reports. Nothing here touches
 * PostgreSQL. Authorization (membership for typing) is enforced earlier by {@link StompDestinationGuard}.
 */
@Controller
public class RealtimeSignalController {

    private final TypingRegistry typingRegistry;
    private final PresenceService presenceService;
    private final SimpMessageSendingOperations messagingTemplate;

    public RealtimeSignalController(TypingRegistry typingRegistry,
                                    PresenceService presenceService,
                                    SimpMessageSendingOperations messagingTemplate) {
        this.typingRegistry = typingRegistry;
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
    }

    public static String typingTopic(Long conversationId) {
        return "/topic/conversations/" + conversationId + "/typing";
    }

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable Long conversationId, Principal principal) {
        Long userId = ((StompPrincipal) principal).userId();
        if (typingRegistry.recordTyping(conversationId, userId)) {
            messagingTemplate.convertAndSend(typingTopic(conversationId), new TypingEvent(conversationId, userId, Instant.now()));
        }
    }

    /** Payload {@code {"status": "IDLE"}} or {@code {"status": "ONLINE"}} from a tab's visibility tracker. */
    @MessageMapping("/presence")
    public void presence(@Payload PresenceUpdate update, Principal principal, SimpMessageHeaderAccessor headers) {
        if (update == null || update.status() == null || headers.getSessionId() == null) {
            return;
        }
        boolean idle = "IDLE".equalsIgnoreCase(update.status());
        presenceService.setIdle(((StompPrincipal) principal).userId(), headers.getSessionId(), idle);
    }

    public record PresenceUpdate(String status) {
    }
}
