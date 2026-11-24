package com.kuetcord.messaging.dto;

import java.util.List;

/**
 * Reconnect catch-up page. {@code missedMessages} are oldest-first and strictly newer than the
 * requested {@code afterMessageId}; when {@code hasMore} is true the client calls again with the
 * last returned ID until the gap is closed.
 */
public record SyncResponseDto(
        Long conversationId,
        List<ChatMessageDto> missedMessages,
        boolean hasMore
) {
}
