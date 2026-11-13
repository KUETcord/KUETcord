package com.kuetcord.auth.service;

import com.kuetcord.auth.dto.AuthResponse;
import com.kuetcord.auth.dto.LoginRequest;
import com.kuetcord.auth.dto.RegisterRequest;
import com.kuetcord.auth.dto.ResendVerificationRequest;
import com.kuetcord.auth.dto.UserSummaryDto;
import com.kuetcord.auth.dto.VerifyEmailRequest;
import com.kuetcord.auth.model.AuthProvider;
import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.model.UserStatus;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.auth.validator.EmailDomainValidator;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.common.exception.ConflictException;
import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Handles institutional email/password registration, verification, and credential authentication.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmailDomainValidator emailDomainValidator;
    private final PasswordService passwordService;
    private final VerificationTokenService verificationTokenService;
    private final EmailDispatchService emailDispatchService;
    private final AuthAccountProvisioner accountProvisioner;

    @Transactional
    public UserSummaryDto register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (!emailDomainValidator.isValid(normalizedEmail)) {
            throw new BadRequestException("Only KUET institutional student emails (@stud.kuet.ac.bd) are allowed");
        }

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("An account with this KUET email already exists");
        }

        Role studentRole = accountProvisioner.requireStudentRole();
        User newUser = User.builder()
                .email(normalizedEmail)
                .displayName(request.displayName().trim())
                .username(accountProvisioner.deriveUniqueUsername(normalizedEmail))
                .passwordHash(passwordService.hash(request.password()))
                .department(trimNullable(request.department()))
                .batch(trimNullable(request.batch()))
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(studentRole)))
                .build();

        User saved = userRepository.save(newUser);
        accountProvisioner.linkIdentityIfAbsent(saved, AuthProvider.LOCAL, normalizedEmail);

        String rawToken = verificationTokenService.issueToken(saved);
        emailDispatchService.sendVerificationEmail(saved.getEmail(), saved.getDisplayName(), rawToken);

        return UserSummaryDto.from(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.getPasswordHash() == null || !passwordService.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new ForbiddenException("Account is suspended or inactive");
        }

        if (!user.isEmailVerified()) {
            throw new ForbiddenException("Email not verified. Please check your KUET inbox.");
        }

        return accountProvisioner.issueBearerResponse(user);
    }

    @Transactional
    public UserSummaryDto verifyEmail(VerifyEmailRequest request) {
        User verifiedUser = verificationTokenService.consumeToken(request.token());
        return UserSummaryDto.from(verifiedUser);
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for email: " + normalizedEmail));

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email address is already verified");
        }

        String rawToken = verificationTokenService.issueToken(user);
        emailDispatchService.sendVerificationEmail(user.getEmail(), user.getDisplayName(), rawToken);
    }

    private static String trimNullable(String value) {
        return value == null ? null : value.trim();
    }
}

