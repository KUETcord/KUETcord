package com.kuetcord.opportunities.ranking;

import com.kuetcord.opportunities.dto.OpportunitySummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.kuetcord.opportunities.ranking.RankingFixtures.context;
import static com.kuetcord.opportunities.ranking.RankingFixtures.ids;
import static com.kuetcord.opportunities.ranking.RankingFixtures.listing;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the "Recently published" ranking strategy. Pure logic, no Spring context.
 */
class RecentlyPublishedStrategyTest {

    private final RecentlyPublishedStrategy strategy = new RecentlyPublishedStrategy();

    @Test
    @DisplayName("Listings are sorted by descending publication time, ignoring deadlines")
    void shouldRankOpportunitiesByRecentlyPublished() {
        List<OpportunitySummaryDto> items = List.of(
                listing(1, Duration.ofDays(1), Duration.ofDays(7)),
                listing(2, Duration.ofDays(-3), Duration.ofMinutes(5)),
                listing(3, Duration.ofDays(30), Duration.ofDays(2)));

        assertEquals(List.of(2L, 3L, 1L), ids(strategy.rank(items, context())));
    }

    @Test
    @DisplayName("Listings published at the same instant fall back to descending id")
    void shouldBreakPublicationTiesByNewestId() {
        List<OpportunitySummaryDto> items = List.of(
                listing(3, Duration.ofDays(1), Duration.ofHours(1)),
                listing(8, Duration.ofDays(2), Duration.ofHours(1)));

        assertEquals(List.of(8L, 3L), ids(strategy.rank(items, context())));
    }
}
