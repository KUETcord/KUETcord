package com.kuetcord.commons.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BanRequest(
        @NotNull(message = "userId is required")
        Long userId,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {
}
