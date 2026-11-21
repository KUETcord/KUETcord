package com.kuetcord.playgrounds.model;

/** What an action on a playground requires. Mapped to roles by {@link PlaygroundRole#grants}. */
public enum PlaygroundPermission {
    /** Read the workspace, board, milestones and decisions. */
    VIEW,
    /** Create, move, assign or delete tasks; add milestones and decisions. */
    EDIT_BOARD,
    /** Edit playground metadata and invite members. */
    MANAGE
}
