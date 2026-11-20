package com.kuetcord.opportunities.ranking;

import java.time.Instant;

/**
 * Everything a ranking strategy may depend on besides the items themselves.
 * {@code now} is passed in (not read from the clock) so strategies are pure and deterministic in tests.
 */
public record RankingContext(Long viewerId, Instant now) {
}
