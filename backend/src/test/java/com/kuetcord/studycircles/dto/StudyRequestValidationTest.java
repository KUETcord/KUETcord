package com.kuetcord.studycircles.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudyRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private final Instant tomorrow = Instant.now().plus(Duration.ofDays(1));

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    @DisplayName("Session: plan payload valid; duration defaults to 60 and is bounded to 15-480 minutes; start must be future")
    void shouldValidateSessionRequest() {
        assertTrue(violatedFields(new CreateStudySessionRequest("DP sprint", tomorrow, 90,
                "Knapsack", "Library 2nd Floor Study Room / Jitsi")).isEmpty());
        assertEquals(60, new CreateStudySessionRequest("x", tomorrow, null, null, null).resolvedDurationMinutes());

        assertEquals(Set.of("durationMinutes"), violatedFields(new CreateStudySessionRequest("x", tomorrow, 14, null, null)));
        assertEquals(Set.of("durationMinutes"), violatedFields(new CreateStudySessionRequest("x", tomorrow, 481, null, null)));
        assertEquals(Set.of("scheduledAt"), violatedFields(new CreateStudySessionRequest("x",
                Instant.now().minusSeconds(60), 60, null, null)));
    }

    @Test
    @DisplayName("Session DTO derives endsAt from start + duration, in UTC")
    void shouldDeriveSessionEnd() {
        // The flat constructor is the one the JPQL projection uses.
        StudySessionDto dto = new StudySessionDto(1L, "x", Instant.parse("2026-11-22T14:00:00Z"), Integer.valueOf(90),
                (String) null, (String) null, (Long) null, (String) null, Instant.now());
        assertEquals(Instant.parse("2026-11-22T15:30:00Z"), dto.endsAt());
        assertNull(dto.scheduledBy());
    }

    @Test
    @DisplayName("Resource: http(s) URL and a category are required")
    void shouldValidateResourceRequest() {
        assertEquals(Set.of("url"), violatedFields(new CreateStudyResourceRequest("x", "file:///etc/passwd",
                com.kuetcord.studycircles.model.ResourceCategory.NOTES)));
        assertEquals(Set.of("category"), violatedFields(new CreateStudyResourceRequest("x", "https://x.org", null)));
    }

    private static Set<String> violatedFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
