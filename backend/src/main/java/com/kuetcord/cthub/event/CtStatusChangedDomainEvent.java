package com.kuetcord.cthub.event;

import com.kuetcord.cthub.model.CtStatus;

import java.time.Instant;

/**
 * Spring ApplicationEvent published upon CT Hub lifecycle transitions so observers
 * react asynchronously after ACID transaction commit (Observer Pattern).
 */
public record CtStatusChangedDomainEvent(
        Long ctEventId,
        Long courseOfferingId,
        String courseCode,
        String title,
        CtStatus previousStatus,
        CtStatus newStatus,
        Instant scheduledAt,
        Long actorId,
        String reason
) {
}
