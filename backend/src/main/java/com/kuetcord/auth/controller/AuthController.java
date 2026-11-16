package com.kuetcord.auth.controller;

import com.kuetcord.auth.dto.AuthResponse;
import com.kuetcord.auth.dto.GoogleAuthRequest;
import com.kuetcord.auth.dto.LoginRequest;
import com.kuetcord.auth.dto.RegisterRequest;
import com.kuetcord.auth.dto.ResendVerificationRequest;
import com.kuetcord.auth.dto.UserSummaryDto;
import com.kuetcord.auth.dto.VerifyEmailRequest;
import com.kuetcord.auth.service.AuthService;
import com.kuetcord.auth.service.GoogleAuthService;
import com.kuetcord.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public institutional authentication endpoints protected by Token Bucket rate limiting.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserSummaryDto>> register(@Valid @RequestBody RegisterRequest request) {
        UserSummaryDto created = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful. Please verify your KUET student email.", created));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<UserSummaryDto>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        UserSummaryDto verified = authService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.ok("Institutional email verified successfully", verified));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.ok("Verification email sent", null));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleSignIn(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = googleAuthService.authenticate(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

