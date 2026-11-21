package com.kuetcord.messaging.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReadStateRequest(
        @NotNull(message = "lastReadMessageId is required")
        @Positive(message = "lastReadMessageId must be positive")
        Long lastReadMessageId
) {
}
