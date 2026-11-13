package com.kuetcord.auth.repository;

import com.kuetcord.auth.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.consumedAt = :now WHERE t.user.id = :userId AND t.consumedAt IS NULL")
    int invalidateActiveTokensForUser(@Param("userId") Long userId, @Param("now") Instant now);
}

