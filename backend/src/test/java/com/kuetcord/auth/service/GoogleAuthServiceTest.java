package com.kuetcord.auth.service;

import com.kuetcord.auth.dto.AuthResponse;
import com.kuetcord.auth.dto.GoogleAuthRequest;
import com.kuetcord.auth.model.AuthProvider;
import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.model.UserStatus;
import com.kuetcord.auth.repository.AuthIdentityRepository;
import com.kuetcord.auth.repository.RoleRepository;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.auth.validator.EmailDomainValidator;
import com.kuetcord.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private GoogleOidcVerifier googleOidcVerifier;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AuthIdentityRepository authIdentityRepository;

    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        JwtTokenService jwtTokenService = new JwtTokenService(
                "c2VjdXJlX2p3dF90ZXN0X3NlY3JldF9mb3JfZ2l0aHViX2FjdGlvbnNfMTIzNDU2Nzg5MA==",
                86400000L
        );
        AuthAccountProvisioner provisioner = new AuthAccountProvisioner(
                userRepository, roleRepository, authIdentityRepository, jwtTokenService
        );
        googleAuthService = new GoogleAuthService(
                googleOidcVerifier,
                new EmailDomainValidator(),
                userRepository,
                authIdentityRepository,
                provisioner
        );
    }

    @Test
    @DisplayName("Should reject forged or invalid Google ID token")
    void shouldRejectTamperedGoogleToken() {
        when(googleOidcVerifier.verify("tampered.token")).thenThrow(new BadCredentialsException("Invalid Google ID token"));

        assertThrows(BadCredentialsException.class,
                () -> googleAuthService.authenticate(new GoogleAuthRequest("tampered.token")));
    }

    @Test
    @DisplayName("Should reject external Gmail accounts even if Google signature is valid")
    void shouldRejectExternalGoogleDomain() {
        when(googleOidcVerifier.verify("valid.gmail.token"))
                .thenReturn(new GoogleTokenPayload("sub-123", "outsider@gmail.com", true, "Outsider"));

        assertThrows(BadRequestException.class,
                () -> googleAuthService.authenticate(new GoogleAuthRequest("valid.gmail.token")));
    }

    @Test
    @DisplayName("Should authenticate valid @stud.kuet.ac.bd Google OIDC token and return JWT")
    void shouldProvisionAndIssueJwtForValidKuetGoogleAccount() {
        Role studentRole = Role.builder().id(1L).name(RoleName.ROLE_STUDENT).build();
        when(googleOidcVerifier.verify("valid.kuet.token"))
                .thenReturn(new GoogleTokenPayload("google-sub-777", "2007001@stud.kuet.ac.bd", true, "Rahim Ahmed"));
        when(authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub-777"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("2007001@stud.kuet.ac.bd"))
                .thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.ROLE_STUDENT)).thenReturn(Optional.of(studentRole));
        when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            u.setEmailVerifiedAt(Instant.now());
            u.setStatus(UserStatus.ACTIVE);
            u.setRoles(Set.of(studentRole));
            return u;
        });

        AuthResponse response = googleAuthService.authenticate(new GoogleAuthRequest("valid.kuet.token"));

        assertNotNull(response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals("2007001@stud.kuet.ac.bd", response.user().email());
        assertTrue(response.user().emailVerified());
    }
}

