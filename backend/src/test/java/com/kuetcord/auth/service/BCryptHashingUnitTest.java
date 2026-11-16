package com.kuetcord.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BCryptHashingUnitTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    @DisplayName("Should hash password with BCrypt work factor 12 and random salt")
    void shouldHashWithWorkFactor12() {
        String raw = "KuetSecure!2026";
        String hash1 = passwordService.hash(raw);
        String hash2 = passwordService.hash(raw);

        assertTrue(hash1.startsWith("$2a$12$"), "Expected BCrypt cost factor 12 ($2a$12$)");
        assertNotEquals(hash1, hash2, "BCrypt must generate distinct salted hashes");
        assertTrue(passwordService.matches(raw, hash1));
        assertTrue(passwordService.matches(raw, hash2));
        assertFalse(passwordService.matches("WrongPassword", hash1));
        assertFalse(passwordService.matches(null, hash1));
    }
}

