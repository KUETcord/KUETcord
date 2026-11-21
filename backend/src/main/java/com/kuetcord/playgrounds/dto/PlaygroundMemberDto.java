package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.PlaygroundRole;

import java.time.Instant;

public record PlaygroundMemberDto(
        Long userId,
        String displayName,
        String username,
        PlaygroundRole role,
        Instant joinedAt
) {
}
