package com.kuetcord.commons.security;

import com.kuetcord.commons.model.Community;
import com.kuetcord.commons.model.CommunityPermission;
import com.kuetcord.commons.model.CommunityRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Discord-style community RBAC: bitmask resolution, rank hierarchy and
 * privilege-escalation guards. Pure logic, no Spring context or database.
 */
class CommunityRoleTest {

    private static final long OWNER_ID = 1L;
    private static final long MOD_ID = 2L;
    private static final long STUDENT_ID = 3L;

    private final Community community = Community.builder().id(10L).ownerId(OWNER_ID).build();
    private final CommunityRole defaultRole = role(0, CommunityPermission.DEFAULT_MEMBER, true);
    private final CommunityRole moderatorRole = role(5, 45L, false); // seeded "Batch Moderator"

    @Test
    @DisplayName("Seeded moderator mask 45 decodes to SEND, KICK, MANAGE_MESSAGES and MUTE only")
    void shouldDecodeSeededModeratorBitmask() {
        assertEquals(EnumSet.of(CommunityPermission.SEND_MESSAGES, CommunityPermission.KICK_MEMBERS,
                        CommunityPermission.MANAGE_MESSAGES, CommunityPermission.MUTE_MEMBERS),
                CommunityPermission.fromMask(45L));
        assertTrue(CommunityPermission.isValidMask(CommunityPermission.ALL));
        assertFalse(CommunityPermission.isValidMask(CommunityPermission.ALL + 1), "Undefined bits must be rejected");
        assertFalse(CommunityPermission.isValidMask(-1L));
    }

    @Test
    @DisplayName("Member authority is the OR of default + assigned roles, ranked by highest position")
    void shouldCombineDefaultAndAssignedRolePermissions() {
        MemberAuthority moderator = MemberAuthority.resolve(community, MOD_ID, true, defaultRole, List.of(moderatorRole));

        assertTrue(moderator.has(CommunityPermission.SEND_MESSAGES));
        assertTrue(moderator.has(CommunityPermission.KICK_MEMBERS));
        assertFalse(moderator.has(CommunityPermission.BAN_MEMBERS));
        assertFalse(moderator.has(CommunityPermission.MANAGE_ROLES));
        assertEquals(5, moderator.rank());
    }

    @Test
    @DisplayName("Owner holds every permission and outranks everyone, even without roles")
    void shouldGrantOwnerEverything() {
        MemberAuthority owner = MemberAuthority.resolve(community, OWNER_ID, true, defaultRole, List.of());
        MemberAuthority moderator = MemberAuthority.resolve(community, MOD_ID, true, defaultRole, List.of(moderatorRole));

        for (CommunityPermission permission : CommunityPermission.values()) {
            assertTrue(owner.has(permission), "Owner must have " + permission);
        }
        assertTrue(owner.outranks(moderator));
        assertFalse(moderator.outranks(owner), "Nobody can act on the owner");
    }

    @Test
    @DisplayName("Non-members (including banned/kicked users) resolve to no permissions at all")
    void shouldDenyNonMembers() {
        MemberAuthority outsider = MemberAuthority.resolve(community, 99L, false, defaultRole, List.of(moderatorRole));

        assertFalse(outsider.member());
        assertFalse(outsider.has(CommunityPermission.SEND_MESSAGES));
        assertFalse(outsider.canGrant(0L));
        assertFalse(outsider.outranks(-5));
    }

    @Test
    @DisplayName("Hierarchy: equal rank cannot act; cannot grant bits you do not hold")
    void shouldBlockEqualRankActionsAndPrivilegeEscalation() {
        MemberAuthority modA = MemberAuthority.resolve(community, MOD_ID, true, defaultRole, List.of(moderatorRole));
        MemberAuthority modB = MemberAuthority.resolve(community, 4L, true, defaultRole, List.of(moderatorRole));
        MemberAuthority student = MemberAuthority.resolve(community, STUDENT_ID, true, defaultRole, List.of());

        assertFalse(modA.outranks(modB), "Same-rank moderators cannot kick each other");
        assertTrue(modA.outranks(student));
        assertTrue(modA.canGrant(CommunityPermission.KICK_MEMBERS.bit() | CommunityPermission.SEND_MESSAGES.bit()));
        assertFalse(modA.canGrant(CommunityPermission.BAN_MEMBERS.bit()), "Moderator cannot hand out BAN");
    }

    private CommunityRole role(int position, long permissions, boolean isDefault) {
        return CommunityRole.builder()
                .communityId(community.getId())
                .name("r" + position)
                .position(position)
                .permissions(permissions)
                .defaultRole(isDefault)
                .build();
    }
}
