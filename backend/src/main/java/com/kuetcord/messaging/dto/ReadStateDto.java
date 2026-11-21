package com.kuetcord.messaging.dto;

/** The effective (monotonic) read marker after an update, plus the remaining unread count. */
public record ReadStateDto(
        Long conversationId,
        Long lastReadMessageId,
        long unreadCount
) {
}
