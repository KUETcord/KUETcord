package com.kuetcord.studycircles.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.studycircles.dto.CreateStudyResourceRequest;
import com.kuetcord.studycircles.dto.StudyResourceDto;
import com.kuetcord.studycircles.service.StudyResourceService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/study-circles/{circleId}/resources")
@RequiredArgsConstructor
public class StudyResourceController {

    private final StudyResourceService resourceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StudyResourceDto>>> list(
            @PathVariable Long circleId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(resourceService.list(circleId, principal.id())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudyResourceDto>> share(
            @PathVariable Long circleId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateStudyResourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Resource shared", resourceService.share(circleId, principal.id(), request)));
    }
}
