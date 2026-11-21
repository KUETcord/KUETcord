package com.kuetcord.messaging.dto;

import java.util.List;

/**
 * One page of history. {@code messages} are in chronological order (oldest first) so the client
 * can prepend the page directly; pass {@code nextCursor} as {@code cursor} to load older messages.
 */
public record MessagePageDto(
        Long conversationId,
        List<ChatMessageDto> messages,
        Long nextCursor,
        boolean hasMore
) {
}
