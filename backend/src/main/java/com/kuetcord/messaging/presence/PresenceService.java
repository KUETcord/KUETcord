package com.kuetcord.messaging.presence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lock-free, in-memory, multi-tab presence engine (zero database I/O).
 * <p>
 * State is {@code userId -> (sessionId -> idle?)}. Tracking the <i>set</i> of sessions rather than a
 * bare counter makes the engine idempotent: Spring may publish {@code SessionDisconnectEvent} more than
 * once for the same session, and a duplicate must not drive a user who still has another tab open to
 * OFFLINE. Each mutation runs inside {@link ConcurrentHashMap#compute}, which is atomic per user, and the
 * resulting transition is published from inside that critical section so two racing tabs can never
 * emit events out of order for the same user.
 * <pre>
 *   sessions == 0                 -> OFFLINE
 *   any session not idle          -> ONLINE
 *   all sessions idle             -> IDLE
 * </pre>
 * Only transitions are broadcast: the 2nd tab connecting or the 1st of two tabs closing is silent.
 */
@Service
public class PresenceService {

    public static final String PRESENCE_TOPIC = "/topic/presence";

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Boolean>> sessionsByUser = new ConcurrentHashMap<>();
    private final SimpMessageSendingOperations messagingTemplate;
    private final Clock clock;

    @Autowired
    public PresenceService(SimpMessageSendingOperations messagingTemplate) {
        this(messagingTemplate, Clock.systemUTC());
    }

    PresenceService(SimpMessageSendingOperations messagingTemplate, Clock clock) {
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    public void sessionConnected(Long userId, String sessionId) {
        mutate(userId, sessions -> sessions.put(sessionId, Boolean.FALSE));
    }

    public void sessionDisconnected(Long userId, String sessionId) {
        mutate(userId, sessions -> sessions.remove(sessionId));
    }

    /** A tab reports it became idle (hidden / inactive) or active again. Unknown sessions are ignored. */
    public void setIdle(Long userId, String sessionId, boolean idle) {
        mutate(userId, sessions -> sessions.computeIfPresent(sessionId, (id, previous) -> idle));
    }

    public PresenceStatus statusOf(Long userId) {
        return statusFrom(sessionsByUser.get(userId));
    }

    public Map<Long, PresenceStatus> statusOf(Collection<Long> userIds) {
        Map<Long, PresenceStatus> result = new LinkedHashMap<>();
        for (Long userId : userIds) {
            result.put(userId, statusOf(userId));
        }
        return result;
    }

    public int sessionCount(Long userId) {
        Map<String, Boolean> sessions = sessionsByUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    private void mutate(Long userId, Consumer<ConcurrentHashMap<String, Boolean>> mutation) {
        if (userId == null) {
            return;
        }
        sessionsByUser.compute(userId, (id, current) -> {
            PresenceStatus before = statusFrom(current);
            ConcurrentHashMap<String, Boolean> next = current != null ? current : new ConcurrentHashMap<>();
            mutation.accept(next);
            PresenceStatus after = statusFrom(next);
            if (before != after) {
                messagingTemplate.convertAndSend(PRESENCE_TOPIC, new PresenceEvent(id, after, Instant.now(clock)));
            }
            return next.isEmpty() ? null : next;
        });
    }

    private static PresenceStatus statusFrom(Map<String, Boolean> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return PresenceStatus.OFFLINE;
        }
        return sessions.containsValue(Boolean.FALSE) ? PresenceStatus.ONLINE : PresenceStatus.IDLE;
    }
}
