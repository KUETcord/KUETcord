package com.kuetcord.opportunities.ranking;

import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.opportunities.dto.OpportunitySummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for strategy selection: the only place that knows which strategies exist.
 */
class OpportunityRankingStrategyResolverTest {

    private final OpportunityRankingStrategyResolver resolver = new OpportunityRankingStrategyResolver(
            List.of(new DeadlineFirstStrategy(), new RecentlyPublishedStrategy()));

    @Test
    @DisplayName("Missing or blank strategy falls back to nearest deadline")
    void shouldDefaultToDeadlineFirst() {
        assertInstanceOf(DeadlineFirstStrategy.class, resolver.resolve(null));
        assertInstanceOf(DeadlineFirstStrategy.class, resolver.resolve("  "));
    }

    @Test
    @DisplayName("Strategy keys are matched case-insensitively")
    void shouldResolveKeysCaseInsensitively() {
        assertInstanceOf(RecentlyPublishedStrategy.class, resolver.resolve("RECENT"));
        assertInstanceOf(DeadlineFirstStrategy.class, resolver.resolve(" Deadline "));
    }

    @Test
    @DisplayName("Unknown strategy is a 400 that lists the supported keys")
    void shouldRejectUnknownStrategy() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> resolver.resolve("popular"));
        assertTrue(ex.getMessage().contains("deadline") && ex.getMessage().contains("recent"));
    }

    @Test
    @DisplayName("Two strategies claiming the same key fail fast at startup")
    void shouldRejectDuplicateKeys() {
        OpportunityRankingStrategy impostor = new OpportunityRankingStrategy() {
            @Override
            public String key() {
                return "deadline";
            }

            @Override
            public List<OpportunitySummaryDto> rank(List<OpportunitySummaryDto> items, RankingContext context) {
                return items;
            }
        };
        assertThrows(IllegalStateException.class,
                () -> new OpportunityRankingStrategyResolver(List.of(new DeadlineFirstStrategy(), impostor)));
    }
}
