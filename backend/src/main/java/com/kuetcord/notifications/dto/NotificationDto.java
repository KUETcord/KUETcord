package com.kuetcord.notifications.dto;

import com.kuetcord.notifications.model.Notification;

import java.time.Instant;

/** One inbox entry, also the payload pushed to {@code /user/queue/notifications}. */
public record NotificationDto(
        Long id,
        String type,
        String title,
        String preview,
        String targetType,
        Long targetId,
        Instant createdAt,
        Instant readAt,
        boolean read) {

    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getPreview(), n.getTargetType(),
                n.getTargetId(), n.getCreatedAt(), n.getReadAt(), n.getReadAt() != null);
    }
}
