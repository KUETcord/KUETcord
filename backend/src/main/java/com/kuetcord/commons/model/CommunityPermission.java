package com.kuetcord.commons.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Discord-style server permission bitflags stored in {@code community_roles.permissions}.
 * <p>
 * Bit values are a persisted contract: never reorder or reuse a bit.
 * The seeded "Batch Moderator" role (45 = SEND_MESSAGES | KICK_MEMBERS | MANAGE_MESSAGES | MUTE_MEMBERS)
 * and "Student Member" role (1 = SEND_MESSAGES) depend on these values.
 */
public enum CommunityPermission {

    SEND_MESSAGES(1L),
    MANAGE_CHANNELS(1L << 1),
    KICK_MEMBERS(1L << 2),
    MANAGE_MESSAGES(1L << 3),
    BAN_MEMBERS(1L << 4),
    MUTE_MEMBERS(1L << 5),
    MANAGE_ROLES(1L << 6),
    MANAGE_SERVER(1L << 7);

    /** Union of every defined bit; the implicit mask of a community owner. */
    public static final long ALL = computeAll();

    /** Permissions granted to the default role of a newly created community. */
    public static final long DEFAULT_MEMBER = SEND_MESSAGES.bit;

    private final long bit;

    CommunityPermission(long bit) {
        this.bit = bit;
    }

    public long bit() {
        return bit;
    }

    public boolean isGrantedBy(long mask) {
        return (mask & bit) == bit;
    }

    public static Set<CommunityPermission> fromMask(long mask) {
        EnumSet<CommunityPermission> result = EnumSet.noneOf(CommunityPermission.class);
        for (CommunityPermission permission : values()) {
            if (permission.isGrantedBy(mask)) {
                result.add(permission);
            }
        }
        return result;
    }

    /** True when {@code mask} only contains defined bits. */
    public static boolean isValidMask(long mask) {
        return mask >= 0 && (mask & ~ALL) == 0;
    }

    private static long computeAll() {
        long all = 0;
        for (CommunityPermission permission : values()) {
            all |= permission.bit;
        }
        return all;
    }
}
