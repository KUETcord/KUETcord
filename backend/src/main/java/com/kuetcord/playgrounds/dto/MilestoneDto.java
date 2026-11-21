package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.MilestoneStatus;

import java.time.Instant;

/** {@code overdue} is derived at read time (PENDING and past its due date), never stored. */
public record MilestoneDto(
        Long id,
        String title,
        String description,
        Instant dueDate,
        MilestoneStatus status,
        boolean overdue,
        Long createdBy,
        Instant createdAt
) {
}
