package com.kuetcord.cthub.event;

/**
 * Post-commit domain event notifying an assigned teacher when a CR proposes,
 * reschedules, or cancels a Class Test.
 */
public record CtTeacherNotificationDomainEvent(
        Long ctEventId,
        String teacherEmail,
        String teacherName,
        String subject,
        String details
) {
}
