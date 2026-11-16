package com.kuetcord.auth.service;

import com.kuetcord.auth.dto.AuthResponse;
import com.kuetcord.auth.dto.UserSummaryDto;
import com.kuetcord.auth.model.AuthIdentity;
import com.kuetcord.auth.model.AuthProvider;
import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.AuthIdentityRepository;
import com.kuetcord.auth.repository.RoleRepository;
import com.kuetcord.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Shared identity provisioning and JWT response builder used by both local and Google OIDC auth flows.
 */
@Component
@RequiredArgsConstructor
public class AuthAccountProvisioner {

    private static final int MAX_USERNAME_LENGTH = 50;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final JwtTokenService jwtTokenService;
    private final SecureRandom random = new SecureRandom();

    public Role requireStudentRole() {
        return roleRepository.findByName(RoleName.ROLE_STUDENT)
                .orElseThrow(() -> new IllegalStateException("Baseline ROLE_STUDENT missing from roles table"));
    }

    public String deriveUniqueUsername(String normalizedEmail) {
        String localPart = normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
                .replaceAll("[^a-zA-Z0-9._-]", "");
        if (localPart.isBlank()) {
            localPart = "student";
        }

        String base = localPart.length() > 40 ? localPart.substring(0, 40) : localPart;
        String candidate = base;
        int attempt = 0;

        while (userRepository.existsByUsernameIgnoreCase(candidate) && attempt < 10) {
            int suffix = 1000 + random.nextInt(9000);
            String raw = base + "_" + suffix;
            candidate = raw.length() > MAX_USERNAME_LENGTH ? raw.substring(0, MAX_USERNAME_LENGTH) : raw;
            attempt++;
        }
        return candidate.toLowerCase(Locale.ROOT);
    }

    public void linkIdentityIfAbsent(User user, AuthProvider provider, String subject) {
        if (!authIdentityRepository.existsByUserIdAndProvider(user.getId(), provider)) {
            authIdentityRepository.save(AuthIdentity.builder()
                    .user(user)
                    .provider(provider)
                    .providerSubject(subject)
                    .build());
        }
    }

    public AuthResponse issueBearerResponse(User user) {
        String token = jwtTokenService.generateToken(user);
        return AuthResponse.bearer(
                token,
                jwtTokenService.getExpirationSeconds(),
                UserSummaryDto.from(user)
        );
    }
}

