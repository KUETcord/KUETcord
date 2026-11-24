package com.kuetcord.messaging.presence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Ephemeral typing state: {@code conversationId -> (userId -> lastBroadcastMillis)}. No database writes.
 * <p>
 * The client already debounces keystrokes to one ping per 3 s; this server-side throttle is the
 * backstop against a modified client spamming typing frames into a large channel. Entries older
 * than {@link #STALE_AFTER_MS} are pruned on access, so memory stays bounded by active typists.
 */
@Component
public class TypingRegistry {

    public static final long THROTTLE_MS = 2_000;
    public static final long STALE_AFTER_MS = 10_000;

    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>> typing = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    @Autowired
    public TypingRegistry() {
        this(System::currentTimeMillis);
    }

    TypingRegistry(LongSupplier clock) {
        this.clock = clock;
    }

    /** Records a typing ping; returns true when it should be broadcast (not throttled). */
    public boolean recordTyping(Long conversationId, Long userId) {
        long now = clock.getAsLong();
        boolean[] accepted = {false};
        ConcurrentHashMap<Long, Long> typists = typing.computeIfAbsent(conversationId, id -> new ConcurrentHashMap<>());
        typists.compute(userId, (id, last) -> {
            if (last != null && now - last < THROTTLE_MS) {
                return last;
            }
            accepted[0] = true;
            return now;
        });
        typists.values().removeIf(timestamp -> now - timestamp > STALE_AFTER_MS);
        return accepted[0];
    }

    /** Called when the user sends a message, so their next keystroke is announced immediately. */
    public void clear(Long conversationId, Long userId) {
        typing.computeIfPresent(conversationId, (id, typists) -> {
            typists.remove(userId);
            return typists.isEmpty() ? null : typists;
        });
    }

    int trackedConversations() {
        return typing.size();
    }
}
