package com.kuetcord.commons.dto;

import com.kuetcord.commons.model.CommunityVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommunityRequest(
        @NotBlank(message = "Community name is required")
        @Size(max = 100, message = "Community name must be at most 100 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        /* Defaults to PUBLIC when omitted. */
        CommunityVisibility visibility
) {
}
