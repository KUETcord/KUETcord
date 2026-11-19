package com.kuetcord.messaging.model;

public enum MemberStatus {
    ACTIVE,
    /** Left voluntarily. */
    LEFT,
    /** Removed by an owner/admin; loses history, send and subscribe rights immediately. */
    REMOVED
}
