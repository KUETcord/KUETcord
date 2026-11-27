package com.kuetcord.cthub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateCtEventRequest(
        @NotNull(message = "Course offering ID is required")
        Long courseOfferingId,

        @NotNull(message = "Assigned teacher ID is required")
        Long teacherId,

        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 150, message = "Title must be between 3 and 150 characters")
        String title,

        @NotNull(message = "Scheduled time is required")
        Instant scheduledAt,

        @Min(value = 10, message = "Duration must be at least 10 minutes")
        @Max(value = 240, message = "Duration must not exceed 240 minutes")
        Integer durationMinutes,

        @Size(max = 4000, message = "Covered topics must not exceed 4000 characters")
        String coveredTopics,

        @Size(max = 100, message = "Exam room must not exceed 100 characters")
        String examRoom,

        @Size(max = 20, message = "Target batch must not exceed 20 characters")
        String targetBatch
) {
    /** Backward-compatible constructor for existing test suites. */
    public CreateCtEventRequest(
            Long courseOfferingId,
            Long teacherId,
            String title,
            Instant scheduledAt,
            Integer durationMinutes,
            String coveredTopics) {
        this(courseOfferingId, teacherId, title, scheduledAt, durationMinutes, coveredTopics, null, null);
    }
}
