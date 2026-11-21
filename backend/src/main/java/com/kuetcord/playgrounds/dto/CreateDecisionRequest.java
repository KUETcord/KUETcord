package com.kuetcord.playgrounds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDecisionRequest(
        @NotBlank(message = "Decision is required")
        @Size(max = 2000, message = "Decision must be at most 2000 characters")
        String decision,

        @Size(max = 5000, message = "Rationale must be at most 5000 characters")
        String rationale
) {
}
