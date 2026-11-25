package com.kuetcord.playgrounds.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.playgrounds.dto.LinkPlaygroundRequest;
import com.kuetcord.playgrounds.dto.LinkedPlaygroundDto;
import com.kuetcord.playgrounds.dto.OpportunityPlaygroundLinkDto;
import com.kuetcord.playgrounds.service.OpportunityPlaygroundLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "Start a team for this contest". Lives in the playgrounds module because it creates and links
 * playgrounds; the opportunity side only needs to exist.
 */
@RestController
@RequestMapping("/api/v1/opportunities/{opportunityId}/playgrounds")
@RequiredArgsConstructor
public class OpportunityPlaygroundController {

    private final OpportunityPlaygroundLinkService linkService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LinkedPlaygroundDto>>> teams(
            @PathVariable Long opportunityId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(linkService.teamsFor(opportunityId, principal.id())));
    }

    /** 201 when a new link was made, 200 when the playground was already linked (idempotent). */
    @PostMapping
    public ResponseEntity<ApiResponse<OpportunityPlaygroundLinkDto>> link(
            @PathVariable Long opportunityId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody(required = false) LinkPlaygroundRequest request) {
        LinkPlaygroundRequest body = request == null ? new LinkPlaygroundRequest(null, null) : request;
        OpportunityPlaygroundLinkDto result = linkService.link(opportunityId, principal.id(), body);
        HttpStatus status = result.newlyLinked() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = result.newlyLinked() ? "Playground linked to opportunity" : "Playground was already linked";
        return ResponseEntity.status(status).body(ApiResponse.ok(message, result));
    }
}
