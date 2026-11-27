package com.kuetcord.auth.validator;

import com.kuetcord.common.academic.YearTerm;
import com.kuetcord.common.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YearTermAndAvatarValidatorTest {

    private final AvatarValidator avatarValidator = new AvatarValidator();

    @Test
    @DisplayName("YearTerm normalizes canonical 1-1..4-2 codes and legacy 1st..8th semesters")
    void yearTermNormalizesAndRejectsInvalidValues() {
        assertEquals("1-1", YearTerm.normalizeNullable("1-1"));
        assertEquals("3-1", YearTerm.normalizeNullable("5th"));
        assertEquals("4-2", YearTerm.normalizeNullable("8th"));
        assertNull(YearTerm.normalizeNullable("   "));
        assertThrows(BadRequestException.class, () -> YearTerm.normalizeNullable("5-1"));
    }

    @Test
    @DisplayName("AvatarValidator accepts compact WebP/JPEG/PNG data URIs and https URLs, rejecting SVG/XSS/oversized payloads")
    void avatarValidatorEnforcesFreeTierAndXssRules() {
        assertTrue(avatarValidator.isValid(null, null));
        assertTrue(avatarValidator.isValid("", null));
        assertTrue(avatarValidator.isValid("data:image/webp;base64,UklGRh4AAABXRUJQVlA4TBEAAAAvAAAAAAfQ//73v/+BiOh/AAA=", null));
        assertTrue(avatarValidator.isValid("https://avatars.githubusercontent.com/u/1?v=4", null));

        // Reject SVG / script / non-HTTPS / oversized blobs
        assertFalse(avatarValidator.isValid("data:image/svg+xml;base64,PHN2Zy8+", null));
        assertFalse(avatarValidator.isValid("javascript:alert(1)", null));
        assertFalse(avatarValidator.isValid("http://insecure.example.com/pic.png", null));

        String oversized = "data:image/webp;base64," + "A".repeat(AvatarValidator.MAX_DATA_URI_LENGTH);
        assertFalse(avatarValidator.isValid(oversized, null));
    }
}
