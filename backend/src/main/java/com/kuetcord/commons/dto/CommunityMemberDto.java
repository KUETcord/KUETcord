package com.kuetcord.commons.dto;

import java.time.Instant;
import java.util.List;

public record CommunityMemberDto(
        Long userId,
        String displayName,
        String username,
        String nickname,
        boolean owner,
        /* Explicitly assigned role IDs; the default role applies to everyone implicitly. */
        List<Long> roleIds,
        Instant joinedAt
) {
}
