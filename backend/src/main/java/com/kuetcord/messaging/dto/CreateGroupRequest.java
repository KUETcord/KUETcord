package com.kuetcord.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code memberIds} are the other participants; the creator is added automatically as GROUP_OWNER.
 * Total participants (creator + distinct others) must be between 3 and 25.
 */
public record CreateGroupRequest(
        @NotBlank(message = "Group title is required")
        @Size(max = 150, message = "Group title must be at most 150 characters")
        String title,

        @NotEmpty(message = "memberIds is required")
        @Size(max = 50, message = "Too many memberIds")
        List<@NotNull Long> memberIds
) {
}
