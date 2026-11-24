package com.kuetcord.studycircles.dto;

import com.kuetcord.studycircles.model.StudyCircleRole;

/** Result of joining; {@code newlyJoined} is false when the caller was already a member. */
public record MembershipDto(
        Long studyCircleId,
        StudyCircleRole role,
        long memberCount,
        boolean newlyJoined
) {
}
