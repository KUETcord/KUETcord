package com.kuetcord.commons.security;

import com.kuetcord.commons.model.Community;
import com.kuetcord.commons.model.CommunityPermission;
import com.kuetcord.commons.model.CommunityRole;

import java.util.Collection;

/**
 * A user's effective standing inside one community: membership, permission mask and rank.
 * <p>
 * Resolution rules (Discord model):
 * <ul>
 *   <li>Owner: every permission and an unbeatable rank, regardless of assigned roles.</li>
 *   <li>Member: OR of the default role's mask and every explicitly assigned role's mask;
 *       rank = highest position among those roles.</li>
 *   <li>Non-member (including banned/kicked users): no permissions.</li>
 * </ul>
 * Pure value type with no I/O so the rules can be unit-tested directly.
 */
public record MemberAuthority(boolean member, boolean owner, long permissions, int rank) {

    public static final MemberAuthority NONE = new MemberAuthority(false, false, 0L, -1);

    public static MemberAuthority resolve(Community community,
                                          Long userId,
                                          boolean isMember,
                                          CommunityRole defaultRole,
                                          Collection<CommunityRole> assignedRoles) {
        if (community.isOwnedBy(userId)) {
            return new MemberAuthority(true, true, CommunityPermission.ALL, Integer.MAX_VALUE);
        }
        if (!isMember) {
            return NONE;
        }
        long mask = 0L;
        int rank = 0;
        if (defaultRole != null) {
            mask |= defaultRole.getPermissions();
            rank = defaultRole.getPosition();
        }
        for (CommunityRole role : assignedRoles) {
            mask |= role.getPermissions();
            rank = Math.max(rank, role.getPosition());
        }
        return new MemberAuthority(true, false, mask & CommunityPermission.ALL, rank);
    }

    public boolean has(CommunityPermission permission) {
        return owner || (member && permission.isGrantedBy(permissions));
    }

    /** Strictly higher rank: you can only act on roles/members ranked below you. */
    public boolean outranks(int otherRank) {
        return owner || (member && rank > otherRank);
    }

    public boolean outranks(MemberAuthority other) {
        return !other.owner && outranks(other.rank);
    }

    /** True when every bit in {@code mask} is one this user already holds (no privilege escalation). */
    public boolean canGrant(long mask) {
        return owner || (member && (mask & ~permissions) == 0);
    }
}
