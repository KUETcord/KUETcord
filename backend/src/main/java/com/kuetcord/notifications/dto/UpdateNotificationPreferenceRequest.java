package com.kuetcord.notifications.dto;

import com.kuetcord.notifications.model.NotificationMode;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** {@code muteUntil} is allowed only with {@code MUTED}; omit it to mute until changed. */
public record UpdateNotificationPreferenceRequest(@NotNull NotificationMode mode, Instant muteUntil) {
}
