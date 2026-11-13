package com.kuetcord.auth.service;

import com.kuetcord.auth.dto.AuthResponse;
import com.kuetcord.auth.dto.GoogleAuthRequest;
import com.kuetcord.auth.model.AuthIdentity;
import com.kuetcord.auth.model.AuthProvider;
import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.model.UserStatus;
import com.kuetcord.auth.repository.AuthIdentityRepository;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.auth.validator.EmailDomainValidator;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.common.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Authenticates users via verified Google OpenID Connect ID tokens while strictly
 * enforcing the @stud.kuet.ac.bd institutional domain boundary.
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleOidcVerifier googleOidcVerifier;
    private final EmailDomainValidator emailDomainValidator;
    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final AuthAccountProvisioner accountProvisioner;

    @Transactional
    public AuthResponse authenticate(GoogleAuthRequest request) {
        GoogleTokenPayload payload = googleOidcVerifier.verify(request.idToken());
        if (!payload.emailVerified()) {
            throw new BadCredentialsException("Google account email is not verified");
        }

        String normalizedEmail = payload.email() != null ? payload.email().trim().toLowerCase(Locale.ROOT) : "";
        if (!emailDomainValidator.isValid(normalizedEmail)) {
            throw new BadRequestException("Only KUET institutional emails (@stud.kuet.ac.bd) are allowed");
        }

        User user = authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, payload.subject())
                .map(AuthIdentity::getUser)
                .orElseGet(() -> findOrCreateUser(normalizedEmail, payload));

        if (!user.isActive()) {
            throw new ForbiddenException("Account is suspended or inactive");
        }

        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(Instant.now());
            userRepository.save(user);
        }

        accountProvisioner.linkIdentityIfAbsent(user, AuthProvider.GOOGLE, payload.subject());
        return accountProvisioner.issueBearerResponse(user);
    }

    private User findOrCreateUser(String normalizedEmail, GoogleTokenPayload payload) {
        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseGet(() -> {
                    Role studentRole = accountProvisioner.requireStudentRole();
                    String displayName = (payload.name() != null && !payload.name().isBlank())
                            ? payload.name().trim()
                            : normalizedEmail;
                    if (displayName.length() > 100) {
                        displayName = displayName.substring(0, 100);
                    }

                    User newUser = User.builder()
                            .email(normalizedEmail)
                            .displayName(displayName)
                            .username(accountProvisioner.deriveUniqueUsername(normalizedEmail))
                            .emailVerifiedAt(Instant.now())
                            .status(UserStatus.ACTIVE)
                            .roles(new HashSet<>(Set.of(studentRole)))
                            .build();
                    return userRepository.save(newUser);
                });
    }
}

