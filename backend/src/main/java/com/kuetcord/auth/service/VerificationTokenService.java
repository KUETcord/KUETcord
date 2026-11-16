package com.kuetcord.auth.service;

import com.kuetcord.auth.model.EmailVerificationToken;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.EmailVerificationTokenRepository;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Generates crypto-random single-use email verification tokens and stores only
 * their SHA-256 digests in PostgreSQL with a strict 15-minute expiration window.
 */
@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    public static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final HexFormat HEX = HexFormat.of();

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private Clock clock = Clock.systemUTC();

    /**
     * Allows deterministic clock injection during unit tests.
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Invalidates any active unconsumed tokens for the user and persists a fresh
     * SHA-256 hashed verification token returning the plaintext token for email dispatch.
     */
    @Transactional
    public String issueToken(User user) {
        Instant now = Instant.now(clock);
        tokenRepository.invalidateActiveTokensForUser(user.getId(), now);

        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String rawToken = HEX.formatHex(randomBytes);

        EmailVerificationToken entity = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(now.plus(TOKEN_TTL))
                .build();

        tokenRepository.save(entity);
        return rawToken;
    }

    /**
     * Validates and consumes a verification token atomically, marking the user's
     * institutional email as verified.
     */
    @Transactional
    public User consumeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Verification token must not be blank");
        }

        String hash = sha256Hex(rawToken.trim());
        EmailVerificationToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestException("Invalid verification token"));

        Instant now = Instant.now(clock);
        if (token.isConsumed()) {
            throw new BadRequestException("Verification token has already been used");
        }
        if (token.isExpired(now)) {
            throw new BadRequestException("Verification token has expired");
        }

        token.setConsumedAt(now);
        tokenRepository.save(token);

        User user = token.getUser();
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
            userRepository.save(user);
        }
        return user;
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM", ex);
        }
    }
}

