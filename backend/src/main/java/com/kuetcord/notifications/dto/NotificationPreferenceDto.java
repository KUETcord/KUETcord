package com.kuetcord.notifications.dto;

import com.kuetcord.notifications.model.NotificationMode;

import java.time.Instant;

/**
 * @param mode          the stored choice, or the default when {@code explicit} is false
 * @param effectiveMode what applies right now (an expired timed mute falls back to {@code defaultMode})
 * @param defaultMode   the default for this conversation type
 * @param explicit      the user has saved a preference for this conversation
 */
public record NotificationPreferenceDto(
        Long conversationId,
        NotificationMode mode,
        Instant muteUntil,
        NotificationMode effectiveMode,
        NotificationMode defaultMode,
        boolean explicit) {
}
