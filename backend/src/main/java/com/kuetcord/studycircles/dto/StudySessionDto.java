package com.kuetcord.studycircles.dto;

import com.kuetcord.common.dto.UserRefDto;

import java.time.Duration;
import java.time.Instant;

/** All instants are UTC; {@code endsAt} is derived from start + duration. */
public record StudySessionDto(
        Long id,
        String title,
        Instant scheduledAt,
        int durationMinutes,
        Instant endsAt,
        String agenda,
        String locationOrLink,
        UserRefDto scheduledBy,
        Instant createdAt
) {

    /** Flat constructor used by the JPQL projection (scheduler joined in the same query). */
    public StudySessionDto(Long id, String title, Instant scheduledAt, Integer durationMinutes, String agenda,
                           String locationOrLink, Long scheduledById, String scheduledByName, Instant createdAt) {
        this(id, title, scheduledAt, durationMinutes, scheduledAt.plus(Duration.ofMinutes(durationMinutes)),
                agenda, locationOrLink, UserRefDto.of(scheduledById, scheduledByName), createdAt);
    }
}
