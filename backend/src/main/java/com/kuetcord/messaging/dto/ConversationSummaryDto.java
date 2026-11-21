package com.kuetcord.messaging.dto;

import com.kuetcord.messaging.model.ConversationType;

import java.time.Instant;

/**
 * One row of the conversation list. For DIRECT conversations {@code displayTitle} is the
 * other participant's name and {@code peerUserId} identifies them (for presence pills).
 */
public record ConversationSummaryDto(
        Long id,
        ConversationType type,
        String title,
        String displayTitle,
        Long peerUserId,
        long memberCount,
        /* Messages from others newer than the caller's read marker (muting never hides this). */
        long unreadCount,
        Instant updatedAt
) {
}
