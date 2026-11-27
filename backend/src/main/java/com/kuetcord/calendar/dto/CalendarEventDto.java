package com.kuetcord.calendar.dto;

import com.kuetcord.calendar.model.CalendarEventType;

import java.time.Instant;

/**
 * Unified calendar event projection aggregated across CT Hub, Assignment Hub,
 * Presentation Hub, and Project Hub without duplicating data.
 */
public record CalendarEventDto(
        String id,
        Long sourceId,
        CalendarEventType type,
        String title,
        Long courseOfferingId,
        String courseCode,
        String courseTitle,
        String department,
        String yearTerm,
        String targetBatch,
        Instant startsAt,
        Instant endsAt,
        Integer durationMinutes,
        String room,
        String status,
        String assignedTeacherName,
        String submissionLink,
        String submittedListLink,
        String hubPath
) {
}
