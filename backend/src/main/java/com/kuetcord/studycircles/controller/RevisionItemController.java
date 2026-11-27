package com.kuetcord.studycircles.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.studycircles.dto.CreateRevisionItemRequest;
import com.kuetcord.studycircles.dto.RevisionItemDto;
import com.kuetcord.studycircles.dto.UpdateRevisionItemRequest;
import com.kuetcord.studycircles.service.RevisionItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/study-circles/{circleId}/revision-items")
@RequiredArgsConstructor
public class RevisionItemController {

    private final RevisionItemService itemService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RevisionItemDto>>> list(
            @PathVariable Long circleId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.list(circleId, principal.id())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RevisionItemDto>> add(
            @PathVariable Long circleId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateRevisionItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Revision item added", itemService.add(circleId, principal.id(), request)));
    }

    @PatchMapping("/{itemId}")
    public ResponseEntity<ApiResponse<RevisionItemDto>> update(
            @PathVariable Long circleId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateRevisionItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                itemService.setDone(circleId, itemId, principal.id(), request.done())));
    }
}
