package com.kuetcord.messaging.presence;

public enum PresenceStatus {
    /** At least one connected session is active. */
    ONLINE,
    /** Connected, but every session reported itself idle (tab hidden / no input). */
    IDLE,
    /** No connected session (closed tabs, logout, or heartbeat eviction). */
    OFFLINE
}
