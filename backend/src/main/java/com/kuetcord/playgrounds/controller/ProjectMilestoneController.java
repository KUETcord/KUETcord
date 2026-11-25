package com.kuetcord.playgrounds.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.playgrounds.dto.CreateMilestoneRequest;
import com.kuetcord.playgrounds.dto.MilestoneDto;
import com.kuetcord.playgrounds.dto.UpdateMilestoneRequest;
import com.kuetcord.playgrounds.service.ProjectMilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/playgrounds/{playgroundId}/milestones")
@RequiredArgsConstructor
public class ProjectMilestoneController {

    private final ProjectMilestoneService milestoneService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MilestoneDto>>> list(
            @PathVariable Long playgroundId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(milestoneService.list(playgroundId, principal.id())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MilestoneDto>> create(
            @PathVariable Long playgroundId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateMilestoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Milestone added", milestoneService.create(playgroundId, principal.id(), request)));
    }

    /** {@code {"status": "COMPLETED"}} marks the milestone reached; {@code "PENDING"} reopens it. */
    @PatchMapping("/{milestoneId}")
    public ResponseEntity<ApiResponse<MilestoneDto>> updateStatus(
            @PathVariable Long playgroundId,
            @PathVariable Long milestoneId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateMilestoneRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                milestoneService.updateStatus(playgroundId, milestoneId, principal.id(), request)));
    }
}
