package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.PlaygroundRole;
import com.kuetcord.playgrounds.model.PlaygroundStatus;
import com.kuetcord.playgrounds.model.PlaygroundVisibility;

import java.time.Instant;

/** One row of "My playgrounds", filled by a JPQL constructor projection (no entity loading). */
public record PlaygroundSummaryDto(
        Long id,
        String title,
        PlaygroundStatus status,
        PlaygroundVisibility visibility,
        PlaygroundRole myRole,
        Long memberCount,
        Instant updatedAt
) {
}
