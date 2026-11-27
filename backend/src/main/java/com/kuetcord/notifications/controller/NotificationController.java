package com.kuetcord.notifications.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.notifications.dto.NotificationPageDto;
import com.kuetcord.notifications.dto.UnreadCountDto;
import com.kuetcord.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Persistent notification inbox. Live updates arrive on {@code /user/queue/notifications}. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageDto>> inbox(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.inbox(principal.id(), cursor, limit, unreadOnly)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountDto>> unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.unreadCount(principal.id())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<UnreadCountDto>> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.markRead(id, principal.id())));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<UnreadCountDto>> markAllRead(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.markAllRead(principal.id())));
    }
}
