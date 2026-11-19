package com.kuetcord.opportunities.ranking;

import com.kuetcord.opportunities.dto.OpportunitySummaryDto;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * "Nearest deadline": open listings first, soonest registration deadline at the top.
 * Listings whose deadline has passed sink below every open one, most recently closed first,
 * so a student never has to scroll past dead contests to find a live one.
 */
@Component
public class DeadlineFirstStrategy implements OpportunityRankingStrategy {

    public static final String KEY = "deadline";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<OpportunitySummaryDto> rank(List<OpportunitySummaryDto> items, RankingContext context) {
        Comparator<OpportunitySummaryDto> openBeforeExpired =
                Comparator.comparing((OpportunitySummaryDto o) -> o.isExpiredAt(context.now()));

        // Within the open group: soonest deadline first. Within the expired group: most recently closed first.
        Comparator<OpportunitySummaryDto> byDeadlineWithinGroup = (a, b) -> {
            int cmp = a.registrationDeadline().compareTo(b.registrationDeadline());
            return a.isExpiredAt(context.now()) ? -cmp : cmp;
        };

        return items.stream()
                .sorted(openBeforeExpired
                        .thenComparing(byDeadlineWithinGroup)
                        .thenComparing(OpportunitySummaryDto::id))
                .toList();
    }
}
