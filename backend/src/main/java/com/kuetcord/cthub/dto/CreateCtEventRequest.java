package com.kuetcord.cthub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreateCtEventRequest(
        @NotNull(message = "Course offering ID is required")
        Long courseOfferingId,

        @NotNull(message = "Assigned teacher ID is required")
        Long teacherId,

        Long coTeacherId,

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

        @Size(max = 255, message = "Exam room must not exceed 255 characters")
        String examRoom,

        @Size(max = 20, message = "Target batch must not exceed 20 characters")
        String targetBatch,

        @Size(max = 20, message = "At most 20 room allocations are allowed per CT")
        List<@Valid RoomAllocationDto> roomAllocations,

        @Size(max = 16, message = "Target department must not exceed 16 characters")
        String targetDepartment,

        @Size(max = 10, message = "Target year-term must not exceed 10 characters")
        String targetYearTerm
) {
    /** Backward-compatible 6-arg constructor for existing test suites. */
    public CreateCtEventRequest(
            Long courseOfferingId,
            Long teacherId,
            String title,
            Instant scheduledAt,
            Integer durationMinutes,
            String coveredTopics) {
        this(courseOfferingId, teacherId, null, title, scheduledAt, durationMinutes, coveredTopics, null, null, null, null, null);
    }

    /** Backward-compatible 8-arg constructor. */
    public CreateCtEventRequest(
            Long courseOfferingId,
            Long teacherId,
            String title,
            Instant scheduledAt,
            Integer durationMinutes,
            String coveredTopics,
            String examRoom,
            String targetBatch) {
        this(courseOfferingId, teacherId, null, title, scheduledAt, durationMinutes, coveredTopics, examRoom, targetBatch, null, null, null);
    }

    /** Backward-compatible 10-arg constructor. */
    public CreateCtEventRequest(
            Long courseOfferingId,
            Long teacherId,
            Long coTeacherId,
            String title,
            Instant scheduledAt,
            Integer durationMinutes,
            String coveredTopics,
            String examRoom,
            String targetBatch,
            List<RoomAllocationDto> roomAllocations) {
        this(courseOfferingId, teacherId, coTeacherId, title, scheduledAt, durationMinutes, coveredTopics,
                examRoom, targetBatch, roomAllocations, null, null);
    }
}
