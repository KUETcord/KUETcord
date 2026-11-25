package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.MilestoneStatus;
import jakarta.validation.constraints.NotNull;

/** {@code COMPLETED} marks the milestone reached (idempotent); {@code PENDING} reopens it. */
public record UpdateMilestoneRequest(
        @NotNull(message = "status is required")
        MilestoneStatus status
) {
}
