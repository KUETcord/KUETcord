package com.kuetcord.cthub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelCtEventRequest(
        @NotBlank(message = "Cancellation reason is required")
        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        String reason,

        @NotNull(message = "Expected version is required for optimistic locking")
        Long expectedVersion
) {
}
