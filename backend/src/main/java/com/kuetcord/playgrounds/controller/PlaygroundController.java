package com.kuetcord.playgrounds.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.playgrounds.dto.AddMemberRequest;
import com.kuetcord.playgrounds.dto.CreatePlaygroundRequest;
import com.kuetcord.playgrounds.dto.PlaygroundDetailDto;
import com.kuetcord.playgrounds.dto.PlaygroundMemberDto;
import com.kuetcord.playgrounds.dto.PlaygroundSummaryDto;
import com.kuetcord.playgrounds.dto.UpdatePlaygroundRequest;
import com.kuetcord.playgrounds.service.PlaygroundService;
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

/**
 * Project Playground workspaces. Per-playground roles (LEAD / CONTRIBUTOR / VIEWER) are enforced by
 * {@code PlaygroundAccessPolicy} inside the services.
 */
@RestController
@RequestMapping("/api/v1/playgrounds")
@RequiredArgsConstructor
public class PlaygroundController {

    private final PlaygroundService playgroundService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlaygroundSummaryDto>>> listMine(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(playgroundService.listMine(principal.id())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlaygroundDetailDto>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreatePlaygroundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Playground created", playgroundService.create(principal.id(), request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlaygroundDetailDto>> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(playgroundService.getDetail(id, principal.id())));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PlaygroundDetailDto>> update(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdatePlaygroundRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(playgroundService.update(id, principal.id(), request)));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<PlaygroundMemberDto>>> addMember(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Member added", playgroundService.addMember(id, principal.id(), request)));
    }
}
