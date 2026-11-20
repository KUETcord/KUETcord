package com.kuetcord.opportunities.ranking;

import com.kuetcord.opportunities.dto.OpportunitySummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.kuetcord.opportunities.ranking.RankingFixtures.context;
import static com.kuetcord.opportunities.ranking.RankingFixtures.ids;
import static com.kuetcord.opportunities.ranking.RankingFixtures.listing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the "Nearest deadline" ranking strategy. Pure logic, no Spring context.
 */
class DeadlineFirstStrategyTest {

    private final DeadlineFirstStrategy strategy = new DeadlineFirstStrategy();

    @Test
    @DisplayName("Open listings are sorted by nearest registration deadline")
    void shouldRankOpportunitiesByDeadlineFirst() {
        List<OpportunitySummaryDto> items = List.of(
                listing(1, Duration.ofDays(10), Duration.ofDays(1)),
                listing(2, Duration.ofHours(6), Duration.ofDays(9)),
                listing(3, Duration.ofDays(3), Duration.ofDays(2)));

        assertEquals(List.of(2L, 3L, 1L), ids(strategy.rank(items, context())));
    }

    @Test
    @DisplayName("Expired listings rank after every open one, most recently closed first")
    void shouldRankExpiredOpportunitiesLast() {
        List<OpportunitySummaryDto> items = List.of(
                listing(1, Duration.ofDays(-30), Duration.ofDays(40)),
                listing(2, Duration.ofDays(20), Duration.ofDays(1)),
                listing(3, Duration.ofHours(-1), Duration.ofDays(5)),
                listing(4, Duration.ofDays(2), Duration.ofDays(3)));

        assertEquals(List.of(4L, 2L, 3L, 1L), ids(strategy.rank(items, context())));
    }

    @Test
    @DisplayName("Equal deadlines fall back to ascending id so the order is stable across requests")
    void shouldBreakDeadlineTiesById() {
        List<OpportunitySummaryDto> items = List.of(
                listing(9, Duration.ofDays(1), Duration.ofDays(1)),
                listing(4, Duration.ofDays(1), Duration.ofDays(2)));

        assertEquals(List.of(4L, 9L), ids(strategy.rank(items, context())));
    }

    @Test
    @DisplayName("Ranking returns a new list and leaves the input untouched")
    void shouldNotMutateInput() {
        List<OpportunitySummaryDto> items = new ArrayList<>(List.of(
                listing(1, Duration.ofDays(5), Duration.ofDays(1)),
                listing(2, Duration.ofDays(1), Duration.ofDays(1))));

        strategy.rank(items, context());

        assertEquals(List.of(1L, 2L), ids(items));
        assertTrue(strategy.rank(List.of(), context()).isEmpty());
    }
}
