package com.kuetcord.auth.service;

/**
 * Abstraction over Google ID token signature, issuer, audience, and expiry verification.
 */
public interface GoogleOidcVerifier {

    GoogleTokenPayload verify(String idToken);
}

