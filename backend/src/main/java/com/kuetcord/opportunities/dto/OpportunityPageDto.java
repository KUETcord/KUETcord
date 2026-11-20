package com.kuetcord.opportunities.dto;

import java.util.List;

/**
 * One page of ranked opportunities.
 *
 * @param strategy   key of the ranking strategy that produced this order
 * @param totalItems number of listings that matched the filters and were ranked
 * @param truncated  true when more listings matched than the ranking window holds;
 *                   the client should narrow its filters
 */
public record OpportunityPageDto(
        List<OpportunitySummaryDto> items,
        int page,
        int size,
        long totalItems,
        String strategy,
        boolean truncated
) {
}
