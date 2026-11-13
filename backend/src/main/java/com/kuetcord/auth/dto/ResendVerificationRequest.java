package com.kuetcord.auth.dto;

import com.kuetcord.auth.validator.KuetEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be validly formatted")
        @KuetEmail
        String email
) {
}

