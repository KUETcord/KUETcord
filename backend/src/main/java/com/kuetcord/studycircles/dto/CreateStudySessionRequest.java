package com.kuetcord.studycircles.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Plan contract (section 2.2): {@code title}, {@code scheduledAt} (ISO-8601 UTC), {@code durationMinutes},
 * {@code agenda}, {@code locationOrLink}.
 */
public record CreateStudySessionRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @NotNull(message = "scheduledAt is required")
        @Future(message = "Sessions must be scheduled in the future")
        Instant scheduledAt,

        @Min(value = 15, message = "A session lasts at least 15 minutes")
        @Max(value = 480, message = "A session lasts at most 8 hours")
        Integer durationMinutes,

        @Size(max = 5000, message = "Agenda must be at most 5000 characters")
        String agenda,

        @Size(max = 255, message = "Location or link must be at most 255 characters")
        String locationOrLink
) {

    public static final int DEFAULT_DURATION_MINUTES = 60;

    public int resolvedDurationMinutes() {
        return durationMinutes == null ? DEFAULT_DURATION_MINUTES : durationMinutes;
    }
}
