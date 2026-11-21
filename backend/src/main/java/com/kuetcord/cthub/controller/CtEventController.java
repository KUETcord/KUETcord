package com.kuetcord.cthub.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.cthub.dto.CancelCtEventRequest;
import com.kuetcord.cthub.dto.CreateCtEventRequest;
import com.kuetcord.cthub.dto.CtEventDetailDto;
import com.kuetcord.cthub.dto.CtEventSummaryDto;
import com.kuetcord.cthub.dto.RescheduleCtEventRequest;
import com.kuetcord.cthub.dto.VerifyCtEventRequest;
import com.kuetcord.cthub.model.CtStatus;
import com.kuetcord.cthub.service.CtEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.Instant;
import java.util.List;

/**
 * Class Test Hub scheduling, verification, rescheduling, and cancellation endpoints.
 */
@RestController
@RequestMapping("/api/v1/ct/events")
@RequiredArgsConstructor
public class CtEventController {

    private final CtEventService ctEventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CtEventSummaryDto>>> listEvents(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) CtStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.ok(ctEventService.listEvents(courseId, status, from, to)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CtEventSummaryDto>> createEvent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateCtEventRequest request) {
        CtEventSummaryDto created = ctEventService.createEvent(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("CT schedule proposed (UNVERIFIED)", created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CtEventDetailDto>> getEventDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(ctEventService.getEventDetail(id)));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('CR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<CtEventSummaryDto>> verifyEvent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestBody(required = false) VerifyCtEventRequest request) {
        CtEventSummaryDto verified = ctEventService.verifyEvent(id, principal.id(), request);
        return ResponseEntity.ok(ApiResponse.ok("CT event verified", verified));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('CR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<CtEventSummaryDto>> rescheduleEvent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody RescheduleCtEventRequest request) {
        CtEventSummaryDto rescheduled = ctEventService.rescheduleEvent(id, principal.id(), request);
        return ResponseEntity.ok(ApiResponse.ok("CT event rescheduled", rescheduled));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<CtEventSummaryDto>> cancelEvent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody CancelCtEventRequest request) {
        CtEventSummaryDto cancelled = ctEventService.cancelEvent(id, principal.id(), request);
        return ResponseEntity.ok(ApiResponse.ok("CT event cancelled", cancelled));
    }
}
