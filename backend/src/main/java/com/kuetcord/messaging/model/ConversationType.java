package com.kuetcord.messaging.model;

/**
 * Every chat surface in KUETcord shares one conversation/message engine (ADR-004).
 * Access rules differ per type and are resolved by {@code ConversationAccessResolver} implementations.
 */
public enum ConversationType {
    /** Text channel inside a community; access derives from community membership and roles. */
    CHANNEL,
    /** One-to-one direct message between exactly two students. */
    DIRECT,
    /** Named group DM with 3-25 participants and Group RBAC. */
    GROUP_DM,
    /** Project Playground chat; access owned by Member C's playground module. */
    PLAYGROUND,
    /** Study Circle chat; access owned by Member C's study-circle module. */
    STUDY_CIRCLE
}
