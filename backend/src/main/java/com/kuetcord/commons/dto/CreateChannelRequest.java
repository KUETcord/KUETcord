package com.kuetcord.commons.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChannelRequest(
        @NotBlank(message = "Channel name is required")
        @Size(max = 100, message = "Channel name must be at most 100 characters")
        String name,

        /* Private channels are visible only to explicit channel members and MANAGE_CHANNELS holders. */
        boolean privateChannel
) {
}
