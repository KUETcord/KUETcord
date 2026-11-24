package com.kuetcord.studycircles.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.studycircles.dto.CreateStudyCircleRequest;
import com.kuetcord.studycircles.dto.MembershipDto;
import com.kuetcord.studycircles.dto.StudyCircleDetailDto;
import com.kuetcord.studycircles.dto.StudyCirclePageDto;
import com.kuetcord.studycircles.service.StudyCircleService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Peer study circles: directory, creation, circle page and joining. */
@RestController
@RequestMapping("/api/v1/study-circles")
@RequiredArgsConstructor
public class StudyCircleController {

    private final StudyCircleService circleService;

    @GetMapping
    public ResponseEntity<ApiResponse<StudyCirclePageDto>> browse(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Long courseOfferingId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                circleService.browse(principal.id(), courseOfferingId, query, mine, page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudyCircleDetailDto>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateStudyCircleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Study circle created", circleService.create(principal.id(), request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StudyCircleDetailDto>> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(circleService.getDetail(id, principal.id())));
    }

    /** 201 on first join, 200 when already a member (idempotent). */
    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<MembershipDto>> join(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        MembershipDto membership = circleService.join(id, principal.id());
        return ResponseEntity.status(membership.newlyJoined() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(membership.newlyJoined() ? "Joined study circle" : "Already a member", membership));
    }
}
