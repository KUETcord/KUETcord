package com.kuetcord.messaging.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.messaging.dto.AddMemberRequest;
import com.kuetcord.messaging.dto.ConversationDetailDto;
import com.kuetcord.messaging.dto.ConversationSummaryDto;
import com.kuetcord.messaging.dto.CreateGroupRequest;
import com.kuetcord.messaging.dto.OpenDirectRequest;
import com.kuetcord.messaging.dto.RenameConversationRequest;
import com.kuetcord.messaging.dto.UpdateGroupRoleRequest;
import com.kuetcord.messaging.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Shared conversation engine: 1:1 DMs, group DMs and Group RBAC.
 * Message history, /sync and read-state endpoints arrive in Phases 2-3.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConversationSummaryDto>>> list(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.listForUser(principal.id())));
    }

    @PostMapping("/direct")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> openDirect(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody OpenDirectRequest request) {
        ConversationService.OpenDirectResult result = conversationService.openDirect(principal.id(), request.userId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.conversation()));
    }

    @PostMapping("/group")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> createGroup(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Group created", conversationService.createGroup(principal.id(), request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@conversationSecurity.isParticipant(#id)")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.getDetail(id, principal.id())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@conversationSecurity.canManageGroup(#id)")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> rename(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RenameConversationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Group renamed", conversationService.rename(id, principal.id(), request.title())));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@conversationSecurity.canAddMember(#id)")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> addMember(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Member added",
                conversationService.addMember(id, principal.id(), request.userId())));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("@conversationSecurity.canRemoveMember(#id, #userId)")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId) {
        conversationService.removeMember(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Member removed", null));
    }

    @PatchMapping("/{id}/members/{userId}/role")
    @PreAuthorize("@conversationSecurity.isOwner(#id)")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> changeRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateGroupRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Role updated",
                conversationService.changeRole(id, principal.id(), userId, request.role())));
    }

    @PostMapping("/{id}/leave")
    @PreAuthorize("@conversationSecurity.isParticipant(#id)")
    public ResponseEntity<ApiResponse<Void>> leave(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        conversationService.leave(id, principal.id());
        return ResponseEntity.ok(ApiResponse.ok("Left conversation", null));
    }
}
