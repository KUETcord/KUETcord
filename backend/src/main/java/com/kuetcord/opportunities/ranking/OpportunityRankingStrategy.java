package com.kuetcord.opportunities.ranking;

import com.kuetcord.opportunities.dto.OpportunitySummaryDto;

import java.util.List;

/**
 * Strategy Pattern (ADR-009): each ranking rule is an isolated, unit-testable bean selected
 * at request time by {@link #key()}, instead of a growing switch in the service or
 * string-built {@code ORDER BY} fragments.
 * <p>
 * Implementations must not mutate {@code items}; they return a new ordered list.
 */
public interface OpportunityRankingStrategy {

    /** Stable public identifier used in {@code ?strategy=}. */
    String key();

    List<OpportunitySummaryDto> rank(List<OpportunitySummaryDto> items, RankingContext context);
}
