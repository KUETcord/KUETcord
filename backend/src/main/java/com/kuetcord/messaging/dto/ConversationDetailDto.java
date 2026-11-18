package com.kuetcord.messaging.dto;

import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.messaging.model.GroupRole;

import java.time.Instant;
import java.util.List;

public record ConversationDetailDto(
        Long id,
        ConversationType type,
        String title,
        String displayTitle,
        Long communityId,
        Long creatorId,
        /* The caller's role; null for conversation types without Group RBAC. */
        GroupRole myRole,
        List<ConversationMemberDto> members,
        Instant createdAt,
        Instant updatedAt
) {
}
