package com.kuetcord.auth.security;

import org.springframework.security.core.GrantedAuthority;

import java.security.Principal;
import java.util.Collection;

/**
 * Immutable authenticated principal stored in Spring SecurityContext after JWT validation.
 */
public record AuthenticatedUser(
        Long id,
        String email,
        Collection<? extends GrantedAuthority> authorities
) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}

