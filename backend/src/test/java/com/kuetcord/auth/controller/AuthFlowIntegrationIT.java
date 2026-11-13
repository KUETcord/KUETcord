package com.kuetcord.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.dto.LoginRequest;
import com.kuetcord.auth.dto.RegisterRequest;
import com.kuetcord.auth.dto.UpdateProfileRequest;
import com.kuetcord.auth.dto.VerifyEmailRequest;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.auth.repository.EmailVerificationTokenRepository;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.auth.service.VerificationTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.clearBuckets();
    }

    @Test
    @DisplayName("Full E2E Auth Lifecycle against PostgreSQL 16 + Flyway + BCrypt + SHA-256 Tokens + JWT")
    void shouldExecuteFullRegistrationVerificationLoginAndProfileUpdateFlow() throws Exception {
        String email = "2007099@stud.kuet.ac.bd";
        RegisterRequest registerReq = new RegisterRequest(
                email,
                "KuetSecret!2026",
                "Tariqul Islam",
                "CSE",
                "2k20"
        );

        // 1. Register user
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.emailVerified").value(false));

        User persisted = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertTrue(persisted.getPasswordHash().startsWith("$2a$12$"));

        // 2. Attempt login before verification -> 403 Forbidden
        LoginRequest loginReq = new LoginRequest(email, "KuetSecret!2026");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Email not verified. Please check your KUET inbox."));

        // 3. Issue fresh verification token, assert hashed at rest, and verify
        String rawToken = verificationTokenService.issueToken(persisted);
        String expectedHash = VerificationTokenService.sha256Hex(rawToken);
        assertTrue(tokenRepository.findByTokenHash(expectedHash).isPresent());
        assertNotEquals(rawToken, expectedHash);

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(rawToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerified").value(true));

        // 4. Reusing consumed token fails -> 400 Bad Request
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(rawToken))))
                .andExpect(status().isBadRequest());

        // 5. Login succeeds -> returns Bearer JWT
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String jwt = loginJson.path("data").path("accessToken").asText();

        // 6. Authenticated GET & PATCH /api/v1/me
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        UpdateProfileRequest patchReq = new UpdateProfileRequest("Tariqul I.", "CSE", "2k20");
        mockMvc.perform(patch("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Tariqul I."));

        assertEquals("Tariqul I.", userRepository.findByEmailIgnoreCase(email).orElseThrow().getDisplayName());
    }
}

