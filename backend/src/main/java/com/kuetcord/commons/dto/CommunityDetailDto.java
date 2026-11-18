package com.kuetcord.commons.dto;

import com.kuetcord.commons.model.CommunityPermission;
import com.kuetcord.commons.model.CommunityVisibility;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CommunityDetailDto(
        Long id,
        String name,
        String description,
        CommunityVisibility visibility,
        Long ownerId,
        Instant createdAt,
        List<ChannelDto> channels,
        List<RoleDto> roles,
        List<CommunityMemberDto> members,
        /* The caller's effective permissions, so the UI can show/hide admin controls. */
        Set<CommunityPermission> myPermissions
) {
}
