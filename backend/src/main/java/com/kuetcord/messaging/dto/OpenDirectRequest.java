package com.kuetcord.messaging.dto;

import jakarta.validation.constraints.NotNull;

public record OpenDirectRequest(
        @NotNull(message = "userId is required")
        Long userId
) {
}
