package com.kuetcord.notifications.dto;

import java.util.List;

/**
 * Keyset page of the inbox, newest first. Pass {@code nextCursor} back as {@code cursor} for older rows.
 * {@code unreadCount} is the whole-inbox badge count, not the count on this page.
 */
public record NotificationPageDto(List<NotificationDto> notifications, Long nextCursor, boolean hasMore, long unreadCount) {
}
