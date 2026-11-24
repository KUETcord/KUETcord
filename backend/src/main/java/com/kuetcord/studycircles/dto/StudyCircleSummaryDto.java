package com.kuetcord.studycircles.dto;

import com.kuetcord.studycircles.model.StudyCircleRole;

import java.time.Instant;

/** Directory card. {@code myRole} is null when the viewer has not joined; {@code nextSessionAt} is the next start. */
public record StudyCircleSummaryDto(
        Long id,
        String name,
        String description,
        CourseRefDto course,
        long memberCount,
        StudyCircleRole myRole,
        Instant nextSessionAt,
        Instant createdAt
) {
}
