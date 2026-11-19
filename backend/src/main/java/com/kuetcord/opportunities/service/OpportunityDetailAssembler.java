package com.kuetcord.opportunities.service;

import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.opportunities.dto.OpportunityDetailDto;
import com.kuetcord.opportunities.model.Opportunity;
import com.kuetcord.opportunities.repository.OpportunityBookmarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Builds the detail view shared by the read path and the write paths (create, verify).
 */
@Component
@RequiredArgsConstructor
class OpportunityDetailAssembler {

    private final UserRepository userRepository;
    private final OpportunityBookmarkRepository bookmarkRepository;

    OpportunityDetailDto toDetail(Opportunity opportunity, Long viewerId) {
        OpportunityDetailDto.Submitter submitter = userRepository.findById(opportunity.getSubmittedBy())
                .map(user -> new OpportunityDetailDto.Submitter(user.getId(), user.getDisplayName()))
                .orElse(new OpportunityDetailDto.Submitter(opportunity.getSubmittedBy(), null));

        boolean bookmarked = bookmarkRepository.existsByUserIdAndOpportunityId(viewerId, opportunity.getId());
        long bookmarkCount = bookmarkRepository.countByOpportunityId(opportunity.getId());

        return OpportunityDetailDto.from(opportunity, submitter, bookmarked, bookmarkCount, Instant.now());
    }
}
