package com.kuetcord.cthub.dto;

import com.kuetcord.cthub.model.CtEventHistory;
import com.kuetcord.cthub.model.CtStatus;

import java.time.Instant;

public record CtEventHistoryDto(
        Long id,
        CtStatus previousStatus,
        CtStatus newStatus,
        Instant previousScheduledAt,
        Instant newScheduledAt,
        String reason,
        Long changedById,
        String changedByName,
        Instant changedAt
) {
    public static CtEventHistoryDto from(CtEventHistory history) {
        return new CtEventHistoryDto(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getPreviousScheduledAt(),
                history.getNewScheduledAt(),
                history.getReason(),
                history.getChangedBy().getId(),
                history.getChangedBy().getDisplayName(),
                history.getChangedAt()
        );
    }
}
