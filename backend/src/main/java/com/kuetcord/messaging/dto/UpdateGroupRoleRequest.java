package com.kuetcord.messaging.dto;

import com.kuetcord.messaging.model.GroupRole;
import jakarta.validation.constraints.NotNull;

/** Only GROUP_ADMIN (promote) and GROUP_MEMBER (demote) are accepted. */
public record UpdateGroupRoleRequest(
        @NotNull(message = "role is required")
        GroupRole role
) {
}
