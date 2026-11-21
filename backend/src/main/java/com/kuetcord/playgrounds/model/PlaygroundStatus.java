package com.kuetcord.playgrounds.model;

/** Project lifecycle. An ARCHIVED playground is frozen: the board, milestones and decisions become read-only. */
public enum PlaygroundStatus {
    PLANNING,
    IN_PROGRESS,
    COMPLETED,
    ARCHIVED
}
