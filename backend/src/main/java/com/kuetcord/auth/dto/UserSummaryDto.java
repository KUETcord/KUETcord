package com.kuetcord.auth.dto;

import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.model.UserStatus;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

public record UserSummaryDto(
        Long id,
        String email,
        String displayName,
        String username,
        String globalRole,
        Set<String> roles,
        String department,
        String batch,
        boolean emailVerified,
        UserStatus status
) {
    public static UserSummaryDto from(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .map(Enum::name)
                .collect(Collectors.toSet());

        String highestRole = user.getRoles().stream()
                .map(Role::getName)
                .max(Comparator.comparingInt(UserSummaryDto::rolePriority))
                .map(name -> name.name().replace("ROLE_", ""))
                .orElse("STUDENT");

        return new UserSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getUsername(),
                highestRole,
                roleNames,
                user.getDepartment(),
                user.getBatch(),
                user.isEmailVerified(),
                user.getStatus()
        );
    }

    private static int rolePriority(RoleName roleName) {
        return switch (roleName) {
            case ROLE_ADMIN -> 4;
            case ROLE_MODERATOR -> 3;
            case ROLE_CR -> 2;
            case ROLE_STUDENT -> 1;
        };
    }
}

