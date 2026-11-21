package com.kuetcord.playgrounds.dto;

import com.kuetcord.playgrounds.model.PlaygroundRole;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
        @NotNull(message = "userId is required")
        Long userId,

        PlaygroundRole role
) {

    public PlaygroundRole resolvedRole() {
        return role == null ? PlaygroundRole.CONTRIBUTOR : role;
    }
}
