package com.kuetcord.opportunities.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kuetcord.opportunities.model.Opportunity;
import com.kuetcord.opportunities.model.OpportunityCategory;
import com.kuetcord.opportunities.model.OpportunityMode;
import com.kuetcord.opportunities.model.VerificationStatus;

import java.time.Instant;

/**
 * Card-sized projection used by the board listing and by every ranking strategy.
 */
public record OpportunitySummaryDto(
        Long id,
        String title,
        OpportunityCategory category,
        String organizer,
        Instant registrationDeadline,
        Instant startDate,
        Instant endDate,
        OpportunityMode mode,
        int teamSizeMin,
        int teamSizeMax,
        VerificationStatus status,
        Instant createdAt,
        boolean expired,
        boolean bookmarked
) {
    public static OpportunitySummaryDto from(Opportunity o, boolean bookmarked, Instant now) {
        return new OpportunitySummaryDto(o.getId(), o.getTitle(), o.getCategory(), o.getOrganizer(),
                o.getRegistrationDeadline(), o.getEventStart(), o.getEventEnd(), o.getMode(),
                o.getTeamSizeMin(), o.getTeamSizeMax(), o.getVerificationStatus(), o.getCreatedAt(),
                o.isExpiredAt(now), bookmarked);
    }

    @JsonIgnore
    public boolean isExpiredAt(Instant now) {
        return registrationDeadline.isBefore(now);
    }
}
