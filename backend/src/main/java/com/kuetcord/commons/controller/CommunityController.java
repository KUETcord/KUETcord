package com.kuetcord.commons.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.commons.dto.BanRequest;
import com.kuetcord.commons.dto.ChannelDto;
import com.kuetcord.commons.dto.CommunityDetailDto;
import com.kuetcord.commons.dto.CommunitySummaryDto;
import com.kuetcord.commons.dto.CreateChannelRequest;
import com.kuetcord.commons.dto.CreateCommunityRequest;
import com.kuetcord.commons.dto.RoleDto;
import com.kuetcord.commons.dto.RoleRequest;
import com.kuetcord.commons.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Campus Commons: Discord-style communities with server RBAC.
 */
@RestController
@RequestMapping("/api/v1/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CommunitySummaryDto>>> browse(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.browse(principal.id(), page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommunityDetailDto>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateCommunityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Community created", communityService.create(principal.id(), request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@communitySecurity.isMember(#id)")
    public ResponseEntity<ApiResponse<CommunityDetailDto>> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.getDetail(id, principal.id())));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<CommunityDetailDto>> join(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("Joined community", communityService.join(id, principal.id())));
    }

    // ----------------------------------------------------------------- channels

    @GetMapping("/{id}/channels")
    @PreAuthorize("@communitySecurity.isMember(#id)")
    public ResponseEntity<ApiResponse<List<ChannelDto>>> channels(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.visibleChannels(id, principal.id())));
    }

    @PostMapping("/{id}/channels")
    @PreAuthorize("@communitySecurity.hasPermission(#id, 'MANAGE_CHANNELS')")
    public ResponseEntity<ApiResponse<ChannelDto>> createChannel(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateChannelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Channel created", communityService.createChannel(id, principal.id(), request)));
    }

    // ----------------------------------------------------------------- roles

    @PostMapping("/{id}/roles")
    @PreAuthorize("@communitySecurity.hasPermission(#id, 'MANAGE_ROLES')")
    public ResponseEntity<ApiResponse<RoleDto>> createRole(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Role created", communityService.createRole(id, principal.id(), request)));
    }

    @PutMapping("/{id}/roles/{roleId}")
    @PreAuthorize("@communitySecurity.hasPermission(#id, 'MANAGE_ROLES')")
    public ResponseEntity<ApiResponse<RoleDto>> updateRole(
            @PathVariable Long id,
            @PathVariable Long roleId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Role updated", communityService.updateRole(id, roleId, principal.id(), request)));
    }

    @PostMapping("/{id}/members/{userId}/roles/{roleId}")
    @PreAuthorize("@communitySecurity.hasPermission(#id, 'MANAGE_ROLES')")
    public ResponseEntity<ApiResponse<Void>> assignRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @PathVariable Long roleId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        communityService.assignRole(id, userId, roleId, principal.id());
        return ResponseEntity.ok(ApiResponse.ok("Role assigned", null));
    }

    // ----------------------------------------------------------------- moderation

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("@communitySecurity.hasPermission(#id, 'KICK_MEMBERS')")
    public ResponseEntity<ApiResponse<Void>> kick(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        communityService.kick(id, userId, principal.id());
        return ResponseEntity.ok(ApiResponse.ok("Member kicked", null));
    }

    @PostMapping("/{id}/bans")
    @PreAuthorize("@communitySecurity.hasPermission(#id, 'BAN_MEMBERS')")
    public ResponseEntity<ApiResponse<Void>> ban(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody BanRequest request) {
        communityService.ban(id, principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Member banned", null));
    }

    @DeleteMapping("/{id}/bans/{userId}")
    @PreAuthorize("@communitySecurity.hasPermission(#id, 'BAN_MEMBERS')")
    public ResponseEntity<ApiResponse<Void>> unban(
            @PathVariable Long id,
            @PathVariable Long userId) {
        communityService.unban(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Ban lifted", null));
    }
}
