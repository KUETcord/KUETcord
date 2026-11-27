package com.kuetcord.notifications.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.notifications.dto.NotificationPreferenceDto;
import com.kuetcord.notifications.dto.UpdateNotificationPreferenceRequest;
import com.kuetcord.notifications.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Notify/Mute for one conversation: {@code ALL_MESSAGES}, {@code MENTIONS_ONLY} or {@code MUTED} (+ {@code muteUntil}). */
@RestController
@RequestMapping("/api/v1/conversations/{id}/notification-preference")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @GetMapping
    @PreAuthorize("@conversationSecurity.isParticipant(#id)")
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> get(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(preferenceService.get(id, principal.id())));
    }

    @PutMapping
    @PreAuthorize("@conversationSecurity.isParticipant(#id)")
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> update(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(preferenceService.update(id, principal.id(), request)));
    }
}
