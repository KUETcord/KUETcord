package com.kuetcord.messaging.dto;

/**
 * Payload of STOMP {@code SEND /app/conversations/{id}/messages}.
 * <p>
 * There is deliberately no {@code senderId}: the sender is always the authenticated STOMP
 * principal, and any extra JSON fields a client sends (e.g. a forged senderId) are ignored.
 * {@code idempotencyKey} is a client-generated UUID reused on every retry of the same message.
 */
public record SendMessageFrame(
        String content,
        String idempotencyKey
) {
}
