package com.kuetcord.playgrounds.model;

/**
 * Role of a student inside one Project Playground ({@code playground_members.role}).
 * <ul>
 *   <li>{@code LEAD}: manages the workspace (metadata, members) and everything a contributor can do.</li>
 *   <li>{@code CONTRIBUTOR}: works the Kanban board, milestones and decision log.</li>
 *   <li>{@code VIEWER}: read-only (mentors, judges, curious classmates on private projects).</li>
 * </ul>
 */
public enum PlaygroundRole {
    LEAD,
    CONTRIBUTOR,
    VIEWER;

    public boolean grants(PlaygroundPermission permission) {
        return switch (permission) {
            case VIEW -> true;
            case EDIT_BOARD -> this == LEAD || this == CONTRIBUTOR;
            case MANAGE -> this == LEAD;
        };
    }
}
