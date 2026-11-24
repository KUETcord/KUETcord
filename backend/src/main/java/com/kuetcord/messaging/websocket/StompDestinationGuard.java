package com.kuetcord.messaging.websocket;

import com.kuetcord.auth.ratelimit.TokenBucket;
import com.kuetcord.messaging.access.ConversationAccessPolicy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authorizes every STOMP {@code SUBSCRIBE} and {@code SEND} frame (fail-closed allowlist).
 * <pre>
 *   SUBSCRIBE /topic/conversations/{id}[/typing]  -> ConversationAccessPolicy.canAccess
 *   SUBSCRIBE /topic/presence                     -> any authenticated user
 *   SUBSCRIBE /user/queue/**                      -> any authenticated user (resolved per session)
 *   SEND      /app/conversations/{id}/messages    -> ConversationAccessPolicy.canSend
 *   SEND      /app/conversations/{id}/typing      -> ConversationAccessPolicy.canAccess
 *   SEND      /app/presence                       -> any authenticated user (own sessions only)
 *   anything else                                 -> denied
 * </pre>
 * Denying direct SENDs to {@code /topic/**} stops clients from forging broadcasts that bypass
 * persistence, and denying raw {@code /queue/**} subscriptions stops snooping on other users'
 * resolved private queues. SEND frames are also rate limited per session (token bucket).
 */
@Component
public class StompDestinationGuard implements ChannelInterceptor {

    public static final int MAX_FRAMES_PER_SECOND = 5;

    private static final Pattern CONVERSATION_TOPIC = Pattern.compile("^/topic/conversations/(\\d+)(/typing)?$");
    private static final Pattern SEND_MESSAGE = Pattern.compile("^/app/conversations/(\\d+)/messages$");
    private static final Pattern SEND_TYPING = Pattern.compile("^/app/conversations/(\\d+)/typing$");
    private static final String PRESENCE_TOPIC = "/topic/presence";
    private static final String SEND_PRESENCE = "/app/presence";
    private static final String USER_QUEUE_PREFIX = "/user/queue/";

    private final ConversationAccessPolicy accessPolicy;
    private final Map<String, TokenBucket> sendBuckets = new ConcurrentHashMap<>();

    public StompDestinationGuard(ConversationAccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscribe(requireUser(accessor), accessor.getDestination());
        } else if (command == StompCommand.SEND) {
            Long userId = requireUser(accessor);
            enforceRateLimit(accessor.getSessionId());
            authorizeSend(userId, accessor.getDestination());
        } else if (command == StompCommand.DISCONNECT && accessor.getSessionId() != null) {
            sendBuckets.remove(accessor.getSessionId());
        }
        return message;
    }

    private void authorizeSubscribe(Long userId, String destination) {
        if (destination == null) {
            throw new AccessDeniedException("Subscription destination is required");
        }
        if (PRESENCE_TOPIC.equals(destination) || destination.startsWith(USER_QUEUE_PREFIX)) {
            return;
        }
        Matcher topic = CONVERSATION_TOPIC.matcher(destination);
        if (topic.matches() && accessPolicy.canAccess(Long.valueOf(topic.group(1)), userId)) {
            return;
        }
        throw new AccessDeniedException("Not allowed to subscribe to " + destination);
    }

    private void authorizeSend(Long userId, String destination) {
        if (destination != null) {
            Matcher message = SEND_MESSAGE.matcher(destination);
            if (message.matches() && accessPolicy.canSend(Long.valueOf(message.group(1)), userId)) {
                return;
            }
            Matcher typing = SEND_TYPING.matcher(destination);
            if (typing.matches() && accessPolicy.canAccess(Long.valueOf(typing.group(1)), userId)) {
                return;
            }
            if (SEND_PRESENCE.equals(destination)) {
                return;
            }
        }
        throw new AccessDeniedException("Not allowed to send to " + destination);
    }

    private void enforceRateLimit(String sessionId) {
        if (sessionId == null) {
            return;
        }
        TokenBucket bucket = sendBuckets.computeIfAbsent(sessionId,
                id -> new TokenBucket(MAX_FRAMES_PER_SECOND, MAX_FRAMES_PER_SECOND));
        if (!bucket.tryConsume()) {
            throw new AccessDeniedException("Rate limit exceeded: max " + MAX_FRAMES_PER_SECOND + " frames per second");
        }
    }

    private static Long requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof StompPrincipal principal) {
            return principal.userId();
        }
        throw new AccessDeniedException("STOMP session is not authenticated");
    }

    /** Visible for tests and metrics: number of sessions with an active rate-limit bucket. */
    public int trackedSessions() {
        return sendBuckets.size();
    }
}
