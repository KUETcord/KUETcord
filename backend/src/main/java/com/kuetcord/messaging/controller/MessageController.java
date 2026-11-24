package com.kuetcord.messaging.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.messaging.dto.MessagePageDto;
import com.kuetcord.messaging.dto.ReadStateDto;
import com.kuetcord.messaging.dto.ReadStateRequest;
import com.kuetcord.messaging.dto.SyncResponseDto;
import com.kuetcord.messaging.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Message history (keyset pagination) and read markers. Sending happens over STOMP.
 */
@RestController
@RequestMapping("/api/v1/conversations/{id}")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/messages")
    @PreAuthorize("@conversationSecurity.isParticipant(#id)")
    public ResponseEntity<ApiResponse<MessagePageDto>> history(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(messageService.history(id, principal.id(), cursor, limit)));
    }

    @GetMapping("/sync")
    @PreAuthorize("@conversationSecurity.isParticipant(#id)")
    public ResponseEntity<ApiResponse<SyncResponseDto>> sync(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam Long afterMessageId,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(messageService.sync(id, principal.id(), afterMessageId, limit)));
    }

    @PostMapping("/read-state")
    @PreAuthorize("@conversationSecurity.isParticipant(#id)")
    public ResponseEntity<ApiResponse<ReadStateDto>> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ReadStateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(messageService.markRead(id, principal.id(), request.lastReadMessageId())));
    }
}
