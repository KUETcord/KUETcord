package com.kuetcord.playgrounds.service;

import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.opportunities.model.Opportunity;
import com.kuetcord.opportunities.repository.OpportunityRepository;
import com.kuetcord.playgrounds.access.PlaygroundAccessPolicy;
import com.kuetcord.playgrounds.dto.CreatePlaygroundRequest;
import com.kuetcord.playgrounds.dto.LinkPlaygroundRequest;
import com.kuetcord.playgrounds.dto.LinkedPlaygroundDto;
import com.kuetcord.playgrounds.dto.OpportunityPlaygroundLinkDto;
import com.kuetcord.playgrounds.model.PlaygroundPermission;
import com.kuetcord.playgrounds.model.PlaygroundVisibility;
import com.kuetcord.playgrounds.repository.OpportunityPlaygroundLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Contest -> team formation: link an opportunity to an existing playground or spin up a new one for it.
 * Creating the playground and the link happen in one transaction, so a failure never leaves an
 * orphan playground that is not attached to its contest.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OpportunityPlaygroundLinkService {

    static final int TITLE_LIMIT = 150;
    static final int PROBLEM_STATEMENT_LIMIT = 5000;
    private static final String TEAM_SUFFIX = " Team";

    private final OpportunityRepository opportunityRepository;
    private final OpportunityPlaygroundLinkRepository linkRepository;
    private final PlaygroundService playgroundService;
    private final PlaygroundAccessPolicy accessPolicy;

    public OpportunityPlaygroundLinkDto link(Long opportunityId, Long userId, LinkPlaygroundRequest request) {
        Opportunity opportunity = requireOpportunity(opportunityId);

        long playgroundId;
        boolean created;
        if (request.playgroundId() != null) {
            // Attaching an existing workspace to a contest is a workspace-level decision: LEAD only.
            accessPolicy.requireWritable(request.playgroundId(), userId, PlaygroundPermission.MANAGE);
            playgroundId = request.playgroundId();
            created = false;
        } else {
            CreatePlaygroundRequest details = request.newPlayground() != null
                    ? request.newPlayground()
                    : defaultPlaygroundFor(opportunity);
            playgroundId = playgroundService.create(userId, details).id();
            created = true;
        }

        boolean newlyLinked = linkRepository.insertIfAbsent(opportunityId, playgroundId, userId) == 1;
        return new OpportunityPlaygroundLinkDto(
                opportunity.getId(),
                opportunity.getTitle(),
                playgroundService.getDetail(playgroundId, userId),
                created,
                newlyLinked);
    }

    /** Teams formed for this opportunity that the viewer may see (PUBLIC, or PRIVATE ones they belong to). */
    @Transactional(readOnly = true)
    public List<LinkedPlaygroundDto> teamsFor(Long opportunityId, Long viewerId) {
        requireOpportunity(opportunityId);
        return linkRepository.findVisiblePlaygrounds(opportunityId, viewerId, PlaygroundVisibility.PUBLIC);
    }

    /** One-click team: named after the contest, described by its listing, open to classmates. */
    static CreatePlaygroundRequest defaultPlaygroundFor(Opportunity opportunity) {
        return new CreatePlaygroundRequest(
                truncate(opportunity.getTitle(), TITLE_LIMIT - TEAM_SUFFIX.length()) + TEAM_SUFFIX,
                opportunity.getDescription() == null ? null : truncate(opportunity.getDescription(), PROBLEM_STATEMENT_LIMIT),
                List.of(opportunity.getCategory().name()),
                PlaygroundVisibility.PUBLIC,
                null,
                null);
    }

    private Opportunity requireOpportunity(Long opportunityId) {
        return opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("Opportunity not found"));
    }

    private static String truncate(String value, int limit) {
        String stripped = value.strip();
        return stripped.length() <= limit ? stripped : stripped.substring(0, limit).strip();
    }
}
