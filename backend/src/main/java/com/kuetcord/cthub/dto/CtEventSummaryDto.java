package com.kuetcord.cthub.dto;

import com.kuetcord.cthub.model.CtEvent;
import com.kuetcord.cthub.model.CtStatus;

import java.time.Instant;

public record CtEventSummaryDto(
        Long id,
        Long courseOfferingId,
        String courseCode,
        String courseTitle,
        String title,
        Instant scheduledAt,
        int durationMinutes,
        String coveredTopics,
        CtStatus status,
        Long submittedById,
        String submittedByName,
        Long verifiedById,
        String verifiedByName,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static CtEventSummaryDto from(CtEvent event) {
        return new CtEventSummaryDto(
                event.getId(),
                event.getCourseOffering().getId(),
                event.getCourseOffering().getCourseCode(),
                event.getCourseOffering().getCourseTitle(),
                event.getTitle(),
                event.getScheduledAt(),
                event.getDurationMinutes(),
                event.getCoveredTopics(),
                event.getStatus(),
                event.getSubmittedBy().getId(),
                event.getSubmittedBy().getDisplayName(),
                event.getVerifiedBy() != null ? event.getVerifiedBy().getId() : null,
                event.getVerifiedBy() != null ? event.getVerifiedBy().getDisplayName() : null,
                event.getVersion(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
