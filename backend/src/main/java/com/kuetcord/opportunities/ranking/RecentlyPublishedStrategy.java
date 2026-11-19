package com.kuetcord.opportunities.ranking;

import com.kuetcord.opportunities.dto.OpportunitySummaryDto;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * "Recently published": newest listings first, regardless of deadline.
 */
@Component
public class RecentlyPublishedStrategy implements OpportunityRankingStrategy {

    public static final String KEY = "recent";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<OpportunitySummaryDto> rank(List<OpportunitySummaryDto> items, RankingContext context) {
        return items.stream()
                .sorted(Comparator.comparing(OpportunitySummaryDto::createdAt)
                        .thenComparing(OpportunitySummaryDto::id)
                        .reversed())
                .toList();
    }
}
