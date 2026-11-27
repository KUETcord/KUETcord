package com.kuetcord.cthub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RescheduleCtEventRequest(
        @NotNull(message = "New scheduled time is required")
        Instant newScheduledAt,

        @NotBlank(message = "Reschedule reason is required")
        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        String reason,

        @NotNull(message = "Expected version is required for optimistic locking")
        Long expectedVersion,

        @Size(max = 100, message = "Exam room must not exceed 100 characters")
        String examRoom
) {
    /** Backward-compatible constructor for existing test suites. */
    public RescheduleCtEventRequest(Instant newScheduledAt, String reason, Long expectedVersion) {
        this(newScheduledAt, reason, expectedVersion, null);
    }
}
