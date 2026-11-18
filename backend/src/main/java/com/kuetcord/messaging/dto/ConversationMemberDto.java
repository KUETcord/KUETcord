package com.kuetcord.messaging.dto;

import com.kuetcord.messaging.model.GroupRole;

import java.time.Instant;

public record ConversationMemberDto(
        Long userId,
        String displayName,
        String username,
        GroupRole groupRole,
        Instant joinedAt
) {
}
