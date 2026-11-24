package com.kuetcord.studycircles.dto;

import com.kuetcord.common.dto.UserRefDto;
import com.kuetcord.studycircles.model.StudyCircleRole;

import java.time.Instant;
import java.util.List;

/** Circle page header. {@code nextSession} is the next session that has not ended yet, or null. */
public record StudyCircleDetailDto(
        Long id,
        String name,
        String description,
        CourseRefDto course,
        UserRefDto creator,
        StudyCircleRole myRole,
        List<CircleMemberDto> members,
        StudySessionDto nextSession,
        long resourceCount,
        Instant createdAt
) {
}
