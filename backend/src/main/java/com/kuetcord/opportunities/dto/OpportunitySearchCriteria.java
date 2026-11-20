package com.kuetcord.opportunities.dto;

import com.kuetcord.opportunities.model.OpportunityCategory;
import com.kuetcord.opportunities.model.OpportunityMode;
import com.kuetcord.opportunities.model.VerificationStatus;

import java.time.Instant;

/**
 * Optional browse facets. A {@code null} facet means "do not filter on it".
 */
public record OpportunitySearchCriteria(
        OpportunityCategory category,
        OpportunityMode mode,
        VerificationStatus status,
        Instant deadlineBefore,
        String query,
        boolean bookmarkedOnly
) {
    public OpportunitySearchCriteria {
        query = (query == null || query.isBlank()) ? null : query.strip();
    }

    public static OpportunitySearchCriteria none() {
        return new OpportunitySearchCriteria(null, null, null, null, null, false);
    }
}
