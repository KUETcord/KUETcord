package com.kuetcord.opportunities.dto;

import com.kuetcord.opportunities.model.Opportunity;
import com.kuetcord.opportunities.model.OpportunityCategory;
import com.kuetcord.opportunities.model.OpportunityMode;
import com.kuetcord.opportunities.model.VerificationStatus;

import java.time.Instant;

public record OpportunityDetailDto(
        Long id,
        String title,
        OpportunityCategory category,
        String organizer,
        String sourceUrl,
        Instant registrationDeadline,
        Instant startDate,
        Instant endDate,
        OpportunityMode mode,
        String eligibility,
        int teamSizeMin,
        int teamSizeMax,
        String description,
        VerificationStatus status,
        Submitter submittedBy,
        Long verifiedBy,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt,
        boolean expired,
        boolean bookmarked,
        long bookmarkCount
) {
    public record Submitter(Long id, String displayName) {
    }

    public static OpportunityDetailDto from(Opportunity o, Submitter submitter, boolean bookmarked,
                                            long bookmarkCount, Instant now) {
        return new OpportunityDetailDto(o.getId(), o.getTitle(), o.getCategory(), o.getOrganizer(), o.getSourceUrl(),
                o.getRegistrationDeadline(), o.getEventStart(), o.getEventEnd(), o.getMode(), o.getEligibility(),
                o.getTeamSizeMin(), o.getTeamSizeMax(), o.getDescription(), o.getVerificationStatus(), submitter,
                o.getVerifiedBy(), o.getVerifiedAt(), o.getCreatedAt(), o.getUpdatedAt(), o.isExpiredAt(now),
                bookmarked, bookmarkCount);
    }
}
