package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.PlaygroundStatus;
import com.kuetcord.playgrounds.model.PlaygroundVisibility;

import java.time.Instant;

/** A team formed for an opportunity, as listed on the opportunity page. */
public record LinkedPlaygroundDto(
        Long id,
        String title,
        PlaygroundStatus status,
        PlaygroundVisibility visibility,
        Long memberCount,
        Instant linkedAt
) {
}
