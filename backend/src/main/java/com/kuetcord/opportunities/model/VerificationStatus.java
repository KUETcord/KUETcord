package com.kuetcord.opportunities.model;

/**
 * Moderation state of a student-submitted listing.
 * <p>
 * Expiry is intentionally not a stored status: it is derived from
 * {@code registration_deadline} at read time, so no scheduler has to flip rows
 * and a listing can never be "expired" in the database while still open in reality.
 */
public enum VerificationStatus {
    UNVERIFIED,
    VERIFIED
}
