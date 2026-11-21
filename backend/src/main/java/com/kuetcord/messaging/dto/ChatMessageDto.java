package com.kuetcord.messaging.dto;

import java.time.Instant;

/**
 * Message as delivered on {@code /topic/conversations/{id}}, in history pages and in /sync.
 * {@code idempotencyKey} lets the sender reconcile its optimistic SENDING bubble with the
 * persisted message; {@code content} is null when the message was deleted.
 */
public record ChatMessageDto(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String content,
        String idempotencyKey,
        Instant createdAt,
        Instant editedAt,
        boolean deleted
) {
}
