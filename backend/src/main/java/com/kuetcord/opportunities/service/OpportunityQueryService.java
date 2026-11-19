package com.kuetcord.opportunities.service;

import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.opportunities.dto.OpportunityDetailDto;
import com.kuetcord.opportunities.dto.OpportunityPageDto;
import com.kuetcord.opportunities.dto.OpportunitySearchCriteria;
import com.kuetcord.opportunities.dto.OpportunitySummaryDto;
import com.kuetcord.opportunities.model.Opportunity;
import com.kuetcord.opportunities.ranking.OpportunityRankingStrategy;
import com.kuetcord.opportunities.ranking.OpportunityRankingStrategyResolver;
import com.kuetcord.opportunities.ranking.RankingContext;
import com.kuetcord.opportunities.repository.OpportunityBookmarkRepository;
import com.kuetcord.opportunities.repository.OpportunityRepository;
import com.kuetcord.opportunities.repository.OpportunitySpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Read side of the Contest &amp; Challenge Board.
 * <p>
 * {@code readOnly = true} puts Hibernate in {@code FlushMode.MANUAL}: no entity snapshots are kept
 * for dirty checking, which matters when a browse request materialises hundreds of listings.
 * <p>
 * Ranking happens in memory over a bounded candidate window ({@link #RANKING_WINDOW} newest
 * matching listings). A campus board holds far fewer live listings than that, and keeping the
 * order in Java is what lets each strategy stay a small, pure, unit-testable class (ADR-009).
 * If the window is ever exceeded the response says so via {@code truncated}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpportunityQueryService {

    static final int RANKING_WINDOW = 500;
    static final int MAX_PAGE_SIZE = 50;

    private final OpportunityRepository opportunityRepository;
    private final OpportunityBookmarkRepository bookmarkRepository;
    private final OpportunityRankingStrategyResolver strategyResolver;
    private final OpportunityDetailAssembler detailAssembler;

    public OpportunityPageDto browse(Long viewerId, OpportunitySearchCriteria criteria,
                                     String strategyKey, int page, int size) {
        OpportunityRankingStrategy strategy = strategyResolver.resolve(strategyKey);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<Opportunity> candidates = opportunityRepository.findAll(
                OpportunitySpecifications.matching(criteria, viewerId),
                PageRequest.of(0, RANKING_WINDOW, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        Set<Long> bookmarkedIds = bookmarkRepository.findOpportunityIdsByUserId(viewerId);
        Instant now = Instant.now();

        List<OpportunitySummaryDto> summaries = candidates.getContent().stream()
                .map(o -> OpportunitySummaryDto.from(o, bookmarkedIds.contains(o.getId()), now))
                .toList();

        List<OpportunitySummaryDto> ranked = strategy.rank(summaries, new RankingContext(viewerId, now));

        int from = (int) Math.min((long) safePage * safeSize, ranked.size());
        int to = Math.min(from + safeSize, ranked.size());

        return new OpportunityPageDto(ranked.subList(from, to), safePage, safeSize, ranked.size(),
                strategy.key(), candidates.getTotalElements() > RANKING_WINDOW);
    }

    public OpportunityDetailDto getDetail(Long opportunityId, Long viewerId) {
        return detailAssembler.toDetail(findOrThrow(opportunityId), viewerId);
    }

    Opportunity findOrThrow(Long opportunityId) {
        return opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("Opportunity not found"));
    }
}
