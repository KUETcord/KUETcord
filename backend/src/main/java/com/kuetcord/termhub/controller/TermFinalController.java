package com.kuetcord.termhub.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.termhub.dto.TermFinalScheduleDto;
import com.kuetcord.termhub.dto.UpsertTermFinalScheduleRequest;
import com.kuetcord.termhub.service.TermFinalService;
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
 * Term Hub REST endpoints for Term Final Examination schedules.
 */
@RestController
@RequestMapping("/api/v1/term-finals")
@RequiredArgsConstructor
public class TermFinalController {

    private final TermFinalService termFinalService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TermFinalScheduleDto>>> list(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String yearTerm,
            @RequestParam(required = false) String batch,
            @RequestParam(required = false) String session) {
        return ResponseEntity.ok(ApiResponse.ok(termFinalService.list(department, yearTerm, batch, session)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<TermFinalScheduleDto>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpsertTermFinalScheduleRequest request) {
        TermFinalScheduleDto created = termFinalService.create(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Term final schedule created", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<TermFinalScheduleDto>> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody UpsertTermFinalScheduleRequest request) {
        TermFinalScheduleDto updated = termFinalService.update(principal.id(), id, request);
        return ResponseEntity.ok(ApiResponse.ok("Term final schedule updated", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        termFinalService.delete(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok("Term final schedule deleted", null));
    }
}
