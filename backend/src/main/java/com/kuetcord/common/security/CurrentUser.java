package com.kuetcord.common.security;

import com.kuetcord.auth.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Reads the authenticated KUETcord principal from the security context.
 * Used by {@code @PreAuthorize} security beans so SpEL expressions stay free of principal plumbing.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<Long> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.ofNullable(user.id());
        }
        return Optional.empty();
    }
}
