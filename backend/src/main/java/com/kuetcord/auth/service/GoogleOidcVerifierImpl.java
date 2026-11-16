package com.kuetcord.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Production Google OIDC ID token verifier validating RS256 signature, issuer,
 * client audience, and token expiration against Google's public JWKS certificates.
 */
@Component
public class GoogleOidcVerifierImpl implements GoogleOidcVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleOidcVerifierImpl(@Value("${kuetcord.google.client-id}") String googleClientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    @Override
    public GoogleTokenPayload verify(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new BadCredentialsException("Google ID token must not be blank");
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new BadCredentialsException("Invalid or expired Google ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            boolean verified = Boolean.TRUE.equals(payload.getEmailVerified());
            String name = payload.get("name") != null ? String.valueOf(payload.get("name")) : payload.getEmail();

            return new GoogleTokenPayload(
                    payload.getSubject(),
                    payload.getEmail(),
                    verified,
                    name
            );
        } catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Failed to verify Google ID token signature", ex);
        }
    }
}

