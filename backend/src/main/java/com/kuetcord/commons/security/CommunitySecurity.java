package com.kuetcord.commons.security;

import com.kuetcord.common.security.CurrentUser;
import com.kuetcord.commons.model.CommunityPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SpEL entry point for community method security, e.g.
 * {@code @PreAuthorize("@communitySecurity.hasPermission(#id, 'MANAGE_ROLES')")}.
 * Unknown communities resolve to "no access" (403) so IDs cannot be probed.
 */
@Component("communitySecurity")
@RequiredArgsConstructor
public class CommunitySecurity {

    private final CommunityAuthorityService authorityService;

    public boolean isMember(Long communityId) {
        return CurrentUser.id()
                .map(userId -> authorityService.authorityOf(communityId, userId).member())
                .orElse(false);
    }

    public boolean hasPermission(Long communityId, String permission) {
        CommunityPermission required = CommunityPermission.valueOf(permission);
        return CurrentUser.id()
                .map(userId -> authorityService.authorityOf(communityId, userId).has(required))
                .orElse(false);
    }
}
