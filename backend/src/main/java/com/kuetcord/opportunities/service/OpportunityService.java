package com.kuetcord.opportunities.service;

import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.opportunities.dto.BookmarkStatusDto;
import com.kuetcord.opportunities.dto.CreateOpportunityRequest;
import com.kuetcord.opportunities.dto.OpportunityDetailDto;
import com.kuetcord.opportunities.model.Opportunity;
import com.kuetcord.opportunities.model.VerificationStatus;
import com.kuetcord.opportunities.repository.OpportunityBookmarkRepository;
import com.kuetcord.opportunities.repository.OpportunityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Write side of the Contest &amp; Challenge Board: submissions, bookmarks and moderator verification.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OpportunityService {

    private final OpportunityRepository opportunityRepository;
    private final OpportunityBookmarkRepository bookmarkRepository;
    private final OpportunityDetailAssembler detailAssembler;

    public OpportunityDetailDto submit(Long submitterId, CreateOpportunityRequest request) {
        Opportunity opportunity = Opportunity.builder()
                .title(request.title().strip())
                .category(request.category())
                .organizer(request.organizer().strip())
                .sourceUrl(request.sourceUrl().strip())
                .registrationDeadline(request.registrationDeadline())
                .eventStart(request.startDate())
                .eventEnd(request.endDate())
                .mode(request.resolvedMode())
                .eligibility(blankToNull(request.eligibility()))
                .teamSizeMin(request.resolvedTeamSizeMin())
                .teamSizeMax(request.resolvedTeamSizeMax())
                .description(blankToNull(request.description()))
                .verificationStatus(VerificationStatus.UNVERIFIED)
                .submittedBy(submitterId)
                .build();

        return detailAssembler.toDetail(opportunityRepository.save(opportunity), submitterId);
    }

    /** Idempotent: bookmarking twice leaves exactly one row and still answers {@code bookmarked=true}. */
    public BookmarkStatusDto bookmark(Long userId, Long opportunityId) {
        ensureExists(opportunityId);
        bookmarkRepository.insertIfAbsent(userId, opportunityId);
        return new BookmarkStatusDto(opportunityId, true, bookmarkRepository.countByOpportunityId(opportunityId));
    }

    /** Idempotent: removing a bookmark that does not exist is not an error. */
    public BookmarkStatusDto removeBookmark(Long userId, Long opportunityId) {
        ensureExists(opportunityId);
        bookmarkRepository.deleteByUserIdAndOpportunityId(userId, opportunityId);
        return new BookmarkStatusDto(opportunityId, false, bookmarkRepository.countByOpportunityId(opportunityId));
    }

    /** Idempotent: verifying an already verified listing keeps the original verifier and timestamp. */
    public OpportunityDetailDto verify(Long moderatorId, Long opportunityId) {
        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("Opportunity not found"));
        if (!opportunity.isVerified()) {
            opportunity.markVerified(moderatorId, Instant.now());
        }
        return detailAssembler.toDetail(opportunity, moderatorId);
    }

    private void ensureExists(Long opportunityId) {
        if (!opportunityRepository.existsById(opportunityId)) {
            throw new ResourceNotFoundException("Opportunity not found");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
