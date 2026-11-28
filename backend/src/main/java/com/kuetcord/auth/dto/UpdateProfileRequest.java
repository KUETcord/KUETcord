package com.kuetcord.auth.dto;

import com.kuetcord.auth.validator.ValidAvatar;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters")
        String displayName,

        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username may only contain letters, digits, dots, underscores, and hyphens")
        String username,

        @Size(max = 50, message = "Department must not exceed 50 characters")
        String department,

        @Size(max = 20, message = "Batch must not exceed 20 characters")
        String batch,

        @Size(max = 10, message = "Year-Term must not exceed 10 characters")
        String yearTerm,

        @Size(max = 20, message = "Roll number must not exceed 20 characters")
        String rollNumber,

        @Size(max = 280, message = "Bio must not exceed 280 characters")
        String bio,

        @ValidAvatar
        String avatarUrl,

        Boolean clearAvatar
) {
    /** Backward-compatible constructor for existing test suites. */
    public UpdateProfileRequest(String displayName, String department, String batch) {
        this(displayName, null, department, batch, null, null, null, null, null);
    }
}
