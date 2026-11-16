package com.kuetcord.auth.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Adaptive credential hashing service enforcing BCrypt with work factor 12.
 */
@Service
public class PasswordService {

    public static final int BCRYPT_COST_FACTOR = 12;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_COST_FACTOR);

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return encoder.matches(rawPassword, encodedPassword);
    }
}

