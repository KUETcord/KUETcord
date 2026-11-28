package com.kuetcord.admin.dto;

import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.UserStatus;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AdminUpdateUserRequest(
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
        String bio,

        Set<RoleName> roles,

        UserStatus status,

        @Size(min = 8, max = 100, message = "Reset password must be between 8 and 100 characters")
        String resetPassword,

        Boolean emailVerified
) {
}
