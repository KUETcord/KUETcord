package com.kuetcord.commons.dto;

import com.kuetcord.commons.model.CommunityPermission;
import com.kuetcord.commons.model.CommunityRole;

import java.util.Set;

public record RoleDto(
        Long id,
        String name,
        String color,
        int position,
        long permissions,
        Set<CommunityPermission> permissionNames,
        boolean defaultRole
) {
    public static RoleDto from(CommunityRole role) {
        return new RoleDto(role.getId(), role.getName(), role.getColor(), role.getPosition(),
                role.getPermissions(), CommunityPermission.fromMask(role.getPermissions()), role.isDefaultRole());
    }
}
