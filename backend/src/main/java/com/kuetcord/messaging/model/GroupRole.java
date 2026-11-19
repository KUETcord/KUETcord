package com.kuetcord.messaging.model;

/**
 * Discord-style Group DM roles.
 */
public enum GroupRole {
    /** Creator; sole authority to promote/demote admins. */
    GROUP_OWNER,
    /** May rename, add members, and remove plain members. */
    GROUP_ADMIN,
    /** May chat and leave. */
    GROUP_MEMBER;

    public boolean canManageMembers() {
        return this == GROUP_OWNER || this == GROUP_ADMIN;
    }
}
