package com.kuetcord.playgrounds.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.playgrounds.dto.CreateDecisionRequest;
import com.kuetcord.playgrounds.dto.DecisionDto;
import com.kuetcord.playgrounds.service.ProjectDecisionService;
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

@RestController
@RequestMapping("/api/v1/playgrounds/{playgroundId}/decisions")
@RequiredArgsConstructor
public class ProjectDecisionController {

    private final ProjectDecisionService decisionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DecisionDto>>> list(
            @PathVariable Long playgroundId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(decisionService.list(playgroundId, principal.id())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DecisionDto>> record(
            @PathVariable Long playgroundId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateDecisionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Decision recorded", decisionService.record(playgroundId, principal.id(), request)));
    }
}
