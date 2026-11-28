package com.kuetcord.cthub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record RescheduleCtEventRequest(
        @NotNull(message = "New scheduled time is required")
        Instant newScheduledAt,

        @NotBlank(message = "Reschedule reason is required")
        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        String reason,

        @NotNull(message = "Expected version is required for optimistic locking")
        Long expectedVersion,

        @Size(max = 255, message = "Exam room must not exceed 255 characters")
        String examRoom,

        @Size(max = 20, message = "At most 20 room allocations are allowed per CT")
        List<@Valid RoomAllocationDto> roomAllocations
) {
    /** Backward-compatible 3-arg constructor. */
    public RescheduleCtEventRequest(Instant newScheduledAt, String reason, Long expectedVersion) {
        this(newScheduledAt, reason, expectedVersion, null, null);
    }

    /** Backward-compatible 4-arg constructor. */
    public RescheduleCtEventRequest(Instant newScheduledAt, String reason, Long expectedVersion, String examRoom) {
        this(newScheduledAt, reason, expectedVersion, examRoom, null);
    }
}
