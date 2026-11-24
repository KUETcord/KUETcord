package com.kuetcord.messaging.presence;

import com.kuetcord.messaging.websocket.StompPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Bridges STOMP session lifecycle into the presence engine.
 * <p>
 * {@link SessionDisconnectEvent} fires for clean DISCONNECTs, closed tabs <b>and</b> heartbeat
 * evictions of zombie sockets (no frames for 3 heartbeat intervals), so a laptop closed mid-session
 * still turns the user OFFLINE within the eviction window.
 */
@Component
public class WebSocketSessionListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionListener.class);

    private final PresenceService presenceService;

    public WebSocketSessionListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Long userId = userId(event.getUser());
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (userId != null && sessionId != null) {
            presenceService.sessionConnected(userId, sessionId);
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Long userId = userId(event.getUser());
        if (userId != null) {
            log.debug("STOMP session {} of user {} closed with {}", event.getSessionId(), userId, event.getCloseStatus());
            presenceService.sessionDisconnected(userId, event.getSessionId());
        }
    }

    private static Long userId(Principal principal) {
        return principal instanceof StompPrincipal stomp ? stomp.userId() : null;
    }
}
