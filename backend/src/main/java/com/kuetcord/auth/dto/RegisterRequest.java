package com.kuetcord.auth.dto;

import com.kuetcord.auth.validator.KuetEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be validly formatted")
        @KuetEmail
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @NotBlank(message = "Display name is required")
        @Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters")
        String displayName,

        @Size(max = 50, message = "Department must not exceed 50 characters")
        String department,

        @Size(max = 20, message = "Batch must not exceed 20 characters")
        String batch,

        @Size(max = 10, message = "Year-Term must not exceed 10 characters")
        String yearTerm,

        @Size(max = 20, message = "Roll number must not exceed 20 characters")
        String rollNumber,

        @Size(max = 280, message = "Bio must not exceed 280 characters")
        String bio
) {
    /** Backward-compatible constructor for existing test suites. */
    public RegisterRequest(String email, String password, String displayName, String department, String batch) {
        this(email, password, displayName, department, batch, null, null, null);
    }
}
