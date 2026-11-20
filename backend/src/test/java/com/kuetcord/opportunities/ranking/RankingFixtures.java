package com.kuetcord.opportunities.ranking;

import com.kuetcord.opportunities.dto.OpportunitySummaryDto;
import com.kuetcord.opportunities.model.OpportunityCategory;
import com.kuetcord.opportunities.model.OpportunityMode;
import com.kuetcord.opportunities.model.VerificationStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Builds summary DTOs relative to a fixed instant so ranking tests never depend on the wall clock.
 */
final class RankingFixtures {

    static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    private RankingFixtures() {
    }

    static OpportunitySummaryDto listing(long id, Duration deadlineFromNow, Duration publishedAgo) {
        Instant deadline = NOW.plus(deadlineFromNow);
        return new OpportunitySummaryDto(id, "Listing " + id, OpportunityCategory.HACKATHON, "KUET Computer Club",
                deadline, deadline.plus(Duration.ofDays(5)), deadline.plus(Duration.ofDays(6)), OpportunityMode.ON_SITE,
                1, 4, VerificationStatus.VERIFIED, NOW.minus(publishedAgo), deadline.isBefore(NOW), false);
    }

    static RankingContext context() {
        return new RankingContext(1L, NOW);
    }

    static List<Long> ids(List<OpportunitySummaryDto> ranked) {
        return ranked.stream().map(OpportunitySummaryDto::id).toList();
    }
}
