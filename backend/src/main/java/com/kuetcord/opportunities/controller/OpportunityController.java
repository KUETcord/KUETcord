package com.kuetcord.opportunities.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.opportunities.dto.BookmarkStatusDto;
import com.kuetcord.opportunities.dto.CreateOpportunityRequest;
import com.kuetcord.opportunities.dto.OpportunityDetailDto;
import com.kuetcord.opportunities.dto.OpportunityPageDto;
import com.kuetcord.opportunities.dto.OpportunitySearchCriteria;
import com.kuetcord.opportunities.model.OpportunityCategory;
import com.kuetcord.opportunities.model.OpportunityMode;
import com.kuetcord.opportunities.model.VerificationStatus;
import com.kuetcord.opportunities.ranking.OpportunityRankingStrategyResolver;
import com.kuetcord.opportunities.service.OpportunityQueryService;
import com.kuetcord.opportunities.service.OpportunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Contest &amp; Challenge Board: hackathons, programming contests, robotics, research calls and
 * case competitions. Deliberately separate from the CT Hub, which only holds academic class tests (ADR-007).
 */
@RestController
@RequestMapping("/api/v1/opportunities")
@RequiredArgsConstructor
public class OpportunityController {

    private final OpportunityQueryService queryService;
    private final OpportunityService opportunityService;

    @GetMapping
    public ResponseEntity<ApiResponse<OpportunityPageDto>> browse(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) OpportunityCategory category,
            @RequestParam(required = false) OpportunityMode mode,
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant deadlineBefore,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "false") boolean bookmarkedOnly,
            @RequestParam(name = "strategy", defaultValue = OpportunityRankingStrategyResolver.DEFAULT_KEY) String strategy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        OpportunitySearchCriteria criteria =
                new OpportunitySearchCriteria(category, mode, status, deadlineBefore, query, bookmarkedOnly);
        return ResponseEntity.ok(ApiResponse.ok(queryService.browse(principal.id(), criteria, strategy, page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OpportunityDetailDto>> submit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateOpportunityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Opportunity submitted for verification",
                        opportunityService.submit(principal.id(), request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OpportunityDetailDto>> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getDetail(id, principal.id())));
    }

    @PostMapping("/{id}/bookmark")
    public ResponseEntity<ApiResponse<BookmarkStatusDto>> bookmark(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("Opportunity bookmarked", opportunityService.bookmark(principal.id(), id)));
    }

    @DeleteMapping("/{id}/bookmark")
    public ResponseEntity<ApiResponse<BookmarkStatusDto>> removeBookmark(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("Bookmark removed", opportunityService.removeBookmark(principal.id(), id)));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<OpportunityDetailDto>> verify(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("Opportunity verified", opportunityService.verify(principal.id(), id)));
    }
}
