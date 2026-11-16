package com.kuetcord.auth.service;

/**
 * Verified identity claims extracted from a Google OpenID Connect ID token.
 */
public record GoogleTokenPayload(
        String subject,
        String email,
        boolean emailVerified,
        String name
) {
}

