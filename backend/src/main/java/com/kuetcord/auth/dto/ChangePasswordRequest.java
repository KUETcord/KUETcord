package com.kuetcord.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @Size(max = 100, message = "Current password must not exceed 100 characters")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "New password must be between 8 and 100 characters")
        String newPassword
) {
}
