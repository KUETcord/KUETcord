package com.kuetcord.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.dto.AuthResponse;
import com.kuetcord.auth.dto.LoginRequest;
import com.kuetcord.auth.dto.RegisterRequest;
import com.kuetcord.auth.dto.UserSummaryDto;
import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.model.UserStatus;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.auth.security.JwtAuthenticationFilter;
import com.kuetcord.auth.security.SecurityConfig;
import com.kuetcord.auth.service.AuthService;
import com.kuetcord.auth.service.GoogleAuthService;
import com.kuetcord.auth.service.JwtTokenService;
import com.kuetcord.auth.service.UserProfileService;
import com.kuetcord.auth.validator.EmailDomainValidator;
import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, UserController.class})
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RateLimitingFilter.class,
        JwtAuthenticationFilter.class,
        JwtTokenService.class,
        EmailDomainValidator.class
})
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private AuthService authService;

    @MockBean
    private GoogleAuthService googleAuthService;

    @MockBean
    private UserProfileService userProfileService;

    @BeforeEach
    void resetRateLimiters() {
        rateLimitingFilter.clearBuckets();
    }

    @Test
    @DisplayName("Contract/Validation: Non-KUET registration email rejected with HTTP 400")
    void shouldRejectNonKuetEmailRegistration() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "outsider@gmail.com",
                "StrongPassword!2026",
                "John Doe",
                "CSE",
                "2k20"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    @DisplayName("Contract/Validation: Valid KUET student registration returns HTTP 201 Created")
    void shouldAcceptValidKuetEmailRegistration() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "2007001@stud.kuet.ac.bd",
                "StrongPassword!2026",
                "Rahim Ahmed",
                "CSE",
                "2k20"
        );
        UserSummaryDto summary = new UserSummaryDto(
                1L, "2007001@stud.kuet.ac.bd", "Rahim Ahmed", "2007001",
                "STUDENT", Set.of("ROLE_STUDENT"), "CSE", "2k20", false, UserStatus.ACTIVE
        );
        when(authService.register(any())).thenReturn(summary);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("2007001@stud.kuet.ac.bd"));
    }

    @Test
    @DisplayName("Security: Unverified local account login blocked with HTTP 403 Forbidden")
    void shouldBlockLoginWhenEmailNotVerified() throws Exception {
        LoginRequest req = new LoginRequest("2007001@stud.kuet.ac.bd", "StrongPassword!2026");
        when(authService.login(any())).thenThrow(new ForbiddenException("Email not verified. Please check your KUET inbox."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Email not verified. Please check your KUET inbox."));
    }

    @Test
    @DisplayName("Security: Invalid credentials return HTTP 401 Unauthorized")
    void shouldReturn401OnBadCredentials() throws Exception {
        LoginRequest req = new LoginRequest("2007001@stud.kuet.ac.bd", "WrongPass");
        when(authService.login(any())).thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Security/Rate-Limit: 11th burst login attempt returns HTTP 429 Too Many Requests")
    void shouldThrottleRapidLoginAttempts() throws Exception {
        LoginRequest req = new LoginRequest("2007001@stud.kuet.ac.bd", "StrongPassword!2026");
        UserSummaryDto summary = new UserSummaryDto(
                1L, "2007001@stud.kuet.ac.bd", "Rahim Ahmed", "2007001",
                "STUDENT", Set.of("ROLE_STUDENT"), "CSE", "2k20", true, UserStatus.ACTIVE
        );
        when(authService.login(any())).thenReturn(AuthResponse.bearer("jwt", 86400, summary));

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "192.168.10.20")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "192.168.10.20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    @DisplayName("Security: Accessing /api/v1/me without JWT returns 401; with valid JWT returns 200")
    void shouldProtectProfileEndpointWithJwt() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());

        User user = User.builder()
                .id(101L)
                .email("2007001@stud.kuet.ac.bd")
                .roles(Set.of(Role.builder().id(1L).name(RoleName.ROLE_STUDENT).build()))
                .build();
        String jwt = jwtTokenService.generateToken(user);

        UserSummaryDto summary = new UserSummaryDto(
                101L, "2007001@stud.kuet.ac.bd", "Rahim Ahmed", "2007001",
                "STUDENT", Set.of("ROLE_STUDENT"), "CSE", "2k20", true, UserStatus.ACTIVE
        );
        when(userProfileService.getProfile(101L)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.email").value("2007001@stud.kuet.ac.bd"));
    }
}

