package com.kuetcord.studycircles.dto;

import com.kuetcord.studycircles.model.StudyCircleRole;

import java.time.Instant;

public record CircleMemberDto(
        Long userId,
        String displayName,
        StudyCircleRole role,
        Instant joinedAt
) {
}
