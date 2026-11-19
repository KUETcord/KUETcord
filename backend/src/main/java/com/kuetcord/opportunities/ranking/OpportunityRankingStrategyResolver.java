package com.kuetcord.opportunities.ranking;

import com.kuetcord.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of every {@link OpportunityRankingStrategy} bean, keyed by {@link OpportunityRankingStrategy#key()}.
 * Adding a strategy means adding one {@code @Component}; nothing here or in the service changes.
 */
@Component
public class OpportunityRankingStrategyResolver {

    public static final String DEFAULT_KEY = DeadlineFirstStrategy.KEY;

    private final Map<String, OpportunityRankingStrategy> strategies;

    public OpportunityRankingStrategyResolver(List<OpportunityRankingStrategy> strategies) {
        this.strategies = strategies.stream().collect(Collectors.toMap(
                s -> s.key().toLowerCase(Locale.ROOT),
                Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("Duplicate ranking strategy key: " + a.key());
                },
                TreeMap::new));
        if (!this.strategies.containsKey(DEFAULT_KEY)) {
            throw new IllegalStateException("Default ranking strategy '" + DEFAULT_KEY + "' is not registered");
        }
    }

    public OpportunityRankingStrategy resolve(String key) {
        if (key == null || key.isBlank()) {
            return strategies.get(DEFAULT_KEY);
        }
        OpportunityRankingStrategy strategy = strategies.get(key.strip().toLowerCase(Locale.ROOT));
        if (strategy == null) {
            throw new BadRequestException("Unknown ranking strategy '" + key + "'. Supported: " + strategies.keySet());
        }
        return strategy;
    }
}
