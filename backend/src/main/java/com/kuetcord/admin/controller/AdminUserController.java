package com.kuetcord.admin.controller;

import com.kuetcord.admin.dto.AdminCreateTeacherRequest;
import com.kuetcord.admin.dto.AdminCreateUserRequest;
import com.kuetcord.admin.dto.AdminUpdateUserRequest;
import com.kuetcord.admin.service.AdminUserService;
import com.kuetcord.auth.dto.UserSummaryDto;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.UserStatus;
import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
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
 * Admin & Moderator full CRUD governance endpoints for direct Teacher provisioning and user management.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserSummaryDto>>> listUsers(
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String yearTerm,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(name = "q", required = false) String query) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminUserService.listUsers(role, department, yearTerm, status, query)));
    }

    @PostMapping("/teachers")
    public ResponseEntity<ApiResponse<UserSummaryDto>> createTeacherDirectly(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AdminCreateTeacherRequest request) {
        UserSummaryDto created = adminUserService.createTeacherDirectly(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Verified teacher account provisioned", created));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserSummaryDto>> createUserDirectly(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AdminCreateUserRequest request) {
        UserSummaryDto created = adminUserService.createUserDirectly(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User account provisioned", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserSummaryDto>> updateUser(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        UserSummaryDto updated = adminUserService.updateUser(principal.id(), id, request);
        return ResponseEntity.ok(ApiResponse.ok("User account updated", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        adminUserService.deleteUser(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok("User account deleted", null));
    }
}
