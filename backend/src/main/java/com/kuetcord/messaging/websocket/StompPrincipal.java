package com.kuetcord.messaging.websocket;

import java.security.Principal;

/**
 * Authenticated identity attached to a STOMP session at CONNECT time.
 * <p>
 * {@link #getName()} returns the <b>user ID</b> (not the email) so that user destinations
 * ({@code /user/queue/...}) and Spring's {@code SimpUserRegistry} are keyed by ID. Fan-out
 * workers and presence tracking then address users without an email lookup.
 */
public record StompPrincipal(Long userId, String email) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
