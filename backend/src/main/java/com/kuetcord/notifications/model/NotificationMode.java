package com.kuetcord.notifications.model;

/**
 * Per-conversation notification setting (stored in {@code notification_preferences.mode}).
 * Each mode is backed by one {@code NotificationPolicyStrategy}.
 */
public enum NotificationMode {
    /** Every message from someone else creates a notification. */
    ALL_MESSAGES,
    /** Only messages that {@code @mention} the recipient create a notification. */
    MENTIONS_ONLY,
    /** Nothing, mentions included, until {@code muteUntil} (or until changed when it is null). */
    MUTED
}
