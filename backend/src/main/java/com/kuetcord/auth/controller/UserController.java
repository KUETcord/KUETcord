package com.kuetcord.auth.controller;

import com.kuetcord.auth.dto.UpdateProfileRequest;
import com.kuetcord.auth.dto.UserSummaryDto;
import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.auth.service.UserProfileService;
import com.kuetcord.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated student self-profile management endpoints.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserSummaryDto>> getCurrentUser(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.getProfile(principal.id())));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<UserSummaryDto>> updateCurrentUser(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserSummaryDto updated = userProfileService.updateProfile(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", updated));
    }
}

