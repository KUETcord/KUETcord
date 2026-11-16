package com.kuetcord.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummaryDto user
) {
    public static AuthResponse bearer(String accessToken, long expiresIn, UserSummaryDto user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}

