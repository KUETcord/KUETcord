package com.kuetcord.support;

import com.kuetcord.auth.model.User;
import com.kuetcord.auth.model.UserStatus;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.auth.service.JwtTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates verified KUET students directly in the database and mints real JWTs for them,
 * so integration tests exercise the production JwtAuthenticationFilter end to end.
 */
@Component
public class MessagingTestUsers {

    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;

    public MessagingTestUsers(UserRepository userRepository, JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
    }

    public record TestUser(Long id, String displayName, String bearer) {
    }

    public TestUser verifiedStudent(String displayName) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .email("b-" + suffix + "@stud.kuet.ac.bd")
                .username("b_" + suffix)
                .displayName(displayName)
                .department("CSE")
                .batch("2k21")
                .emailVerifiedAt(Instant.now())
                .status(UserStatus.ACTIVE)
                .build());
        return new TestUser(user.getId(), displayName, "Bearer " + jwtTokenService.generateToken(user));
    }

    public static String authHeader() {
        return HttpHeaders.AUTHORIZATION;
    }
}
