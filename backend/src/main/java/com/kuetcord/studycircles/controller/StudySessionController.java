package com.kuetcord.studycircles.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.studycircles.dto.CreateStudySessionRequest;
import com.kuetcord.studycircles.dto.StudySessionDto;
import com.kuetcord.studycircles.service.StudySessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/study-circles/{circleId}/sessions")
@RequiredArgsConstructor
public class StudySessionController {

    private final StudySessionService sessionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StudySessionDto>>> list(
            @PathVariable Long circleId,
            @RequestParam(defaultValue = "false") boolean includePast,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.list(circleId, principal.id(), includePast)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudySessionDto>> schedule(
            @PathVariable Long circleId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateStudySessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Study session scheduled", sessionService.schedule(circleId, principal.id(), request)));
    }
}
