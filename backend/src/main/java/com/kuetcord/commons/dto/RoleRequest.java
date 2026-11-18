package com.kuetcord.commons.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RoleRequest(
        @NotBlank(message = "Role name is required")
        @Size(max = 50, message = "Role name must be at most 50 characters")
        String name,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a hex value like #2ECC71")
        String color,

        @NotNull(message = "Permissions bitmask is required")
        @PositiveOrZero(message = "Permissions bitmask cannot be negative")
        Long permissions,

        /* Rank inside the community; 0 is reserved for the default role. Defaults to 1. */
        @Min(value = 1, message = "Position must be at least 1")
        @Max(value = 1000, message = "Position must be at most 1000")
        Integer position
) {
}
