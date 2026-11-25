package com.kuetcord.playgrounds.dto;

import com.kuetcord.opportunities.model.OpportunityCategory;

import java.time.Instant;

/** A contest a playground was formed for, shown in the workspace header. */
public record LinkedOpportunityDto(
        Long id,
        String title,
        OpportunityCategory category,
        Instant registrationDeadline
) {
}
