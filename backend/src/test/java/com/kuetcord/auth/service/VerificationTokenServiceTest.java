package com.kuetcord.auth.service;

import com.kuetcord.auth.model.EmailVerificationToken;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.EmailVerificationTokenRepository;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationTokenServiceTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    private VerificationTokenService service;
    private Instant fixedNow;

    @BeforeEach
    void setUp() {
        fixedNow = Instant.parse("2026-11-14T10:00:00Z");
        service = new VerificationTokenService(tokenRepository, userRepository);
        service.setClock(Clock.fixed(fixedNow, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Should store only SHA-256 token hash with 15-minute TTL and invalidate older tokens")
    void shouldStoreSha256HashAndInvalidatePriorTokens() {
        User user = User.builder().id(10L).email("2007001@stud.kuet.ac.bd").build();

        String rawToken = service.issueToken(user);

        verify(tokenRepository).invalidateActiveTokensForUser(10L, fixedNow);

        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(captor.capture());

        EmailVerificationToken saved = captor.getValue();
        assertEquals(64, rawToken.length());
        assertNotEquals(rawToken, saved.getTokenHash(), "Plaintext token must never be stored in DB");
        assertEquals(VerificationTokenService.sha256Hex(rawToken), saved.getTokenHash());
        assertEquals(fixedNow.plusSeconds(15 * 60), saved.getExpiresAt());
    }

    @Test
    @DisplayName("Should verify user and mark token consumed when valid")
    void shouldConsumeValidToken() {
        User user = User.builder().id(10L).email("2007001@stud.kuet.ac.bd").build();
        String raw = "sample_raw_token_123";
        String hash = VerificationTokenService.sha256Hex(raw);

        EmailVerificationToken stored = EmailVerificationToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(hash)
                .expiresAt(fixedNow.plusSeconds(300))
                .build();

        when(tokenRepository.findByTokenHash(eq(hash))).thenReturn(Optional.of(stored));

        User verified = service.consumeToken(raw);

        assertNotNull(verified.getEmailVerifiedAt());
        assertEquals(fixedNow, stored.getConsumedAt());
        verify(userRepository).save(any(User.class));
        verify(tokenRepository).save(stored);
    }

    @Test
    @DisplayName("Should reject expired verification token")
    void shouldRejectExpiredToken() {
        User user = User.builder().id(10L).email("2007001@stud.kuet.ac.bd").build();
        String raw = "expired_token";
        String hash = VerificationTokenService.sha256Hex(raw);

        EmailVerificationToken expired = EmailVerificationToken.builder()
                .id(2L)
                .user(user)
                .tokenHash(hash)
                .expiresAt(fixedNow.minusSeconds(1))
                .build();

        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(expired));

        assertThrows(BadRequestException.class, () -> service.consumeToken(raw));
    }

    @Test
    @DisplayName("Should reject already consumed verification token")
    void shouldRejectReusedToken() {
        User user = User.builder().id(10L).email("2007001@stud.kuet.ac.bd").build();
        String raw = "used_token";
        String hash = VerificationTokenService.sha256Hex(raw);

        EmailVerificationToken consumed = EmailVerificationToken.builder()
                .id(3L)
                .user(user)
                .tokenHash(hash)
                .expiresAt(fixedNow.plusSeconds(300))
                .consumedAt(fixedNow.minusSeconds(30))
                .build();

        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(consumed));

        assertThrows(BadRequestException.class, () -> service.consumeToken(raw));
    }
}

