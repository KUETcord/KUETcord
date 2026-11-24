package com.kuetcord.pulse.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.pulse.dto.AnnouncementDto;
import com.kuetcord.pulse.dto.AnnouncementPageDto;
import com.kuetcord.pulse.dto.CreateAnnouncementRequest;
import com.kuetcord.pulse.model.AnnouncementCategory;
import com.kuetcord.pulse.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
 * Campus Pulse. Every signed-in student reads the feed; only verified staff (teachers, moderators,
 * admins) publish, so a notice in the feed is never a student rumour.
 */
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public ResponseEntity<ApiResponse<AnnouncementPageDto>> feed(
            @RequestParam(required = false) AnnouncementCategory category,
            @RequestParam(required = false) String organization,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(announcementService.feed(category, organization, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AnnouncementDto>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(announcementService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<AnnouncementDto>> publish(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateAnnouncementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Announcement published", announcementService.publish(principal.id(), request)));
    }
}
