package com.kuetcord.notifications.dto;

/** Badge count; {@code updated} is how many rows a mark-read call changed (0 for plain reads). */
public record UnreadCountDto(long unreadCount, int updated) {
}
