package com.kuetcord.studycircles.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateRevisionItemRequest(
        @NotNull(message = "done is required")
        Boolean done
) {
}
