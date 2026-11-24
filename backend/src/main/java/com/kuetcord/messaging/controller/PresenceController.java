package com.kuetcord.messaging.controller;

import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.messaging.presence.PresenceEvent;
import com.kuetcord.messaging.presence.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Initial presence snapshot for a screen (member list, DM list); live changes then arrive on
 * {@code /topic/presence}. Served from memory: no database access.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class PresenceController {

    public static final int MAX_IDS = 200;

    private final PresenceService presenceService;

    @GetMapping("/presence")
    public ResponseEntity<ApiResponse<List<PresenceEvent>>> presence(@RequestParam List<Long> ids) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>(ids);
        if (unique.size() > MAX_IDS) {
            throw new BadRequestException("At most " + MAX_IDS + " user ids per request");
        }
        Instant now = Instant.now();
        List<PresenceEvent> snapshot = presenceService.statusOf(unique).entrySet().stream()
                .map(entry -> new PresenceEvent(entry.getKey(), entry.getValue(), now))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(snapshot));
    }
}
