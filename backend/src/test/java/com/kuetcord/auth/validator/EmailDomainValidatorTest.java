package com.kuetcord.auth.validator;

import com.kuetcord.auth.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailDomainValidatorTest {

    private EmailDomainValidator domainValidator;
    private Validator beanValidator;

    @BeforeEach
    void setUp() {
        domainValidator = new EmailDomainValidator();
        beanValidator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2007001@stud.kuet.ac.bd",
            "student.1907042@stud.kuet.ac.bd",
            "2107099@STUD.KUET.AC.BD",
            "  2007005@stud.kuet.ac.bd  "
    })
    @DisplayName("Should accept valid KUET student emails")
    void shouldAcceptValidInstitutionalEmails(String email) {
        assertTrue(domainValidator.isValid(email));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "student@gmail.com",
            "user@yahoo.com",
            "faculty@kuet.ac.bd",
            "2007001@stud.kuet.ac.bd.evil.com",
            "bad space@stud.kuet.ac.bd",
            "@stud.kuet.ac.bd",
            "",
            "   "
    })
    @DisplayName("Should reject external, spoofed subdomain, and malformed emails")
    void shouldRejectNonKuetOrMalformedEmails(String email) {
        assertFalse(domainValidator.isValid(email));
    }

    @Test
    @DisplayName("Bean Validation @KuetEmail should reject non-KUET email on RegisterRequest")
    void beanValidationShouldRejectNonKuetEmail() {
        RegisterRequest invalidRequest = new RegisterRequest(
                "outsider@gmail.com",
                "StrongPassword!2026",
                "External User",
                "CSE",
                "2k20"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = beanValidator.validate(invalidRequest);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    @DisplayName("Bean Validation @KuetEmail should pass valid KUET email on RegisterRequest")
    void beanValidationShouldAcceptKuetEmail() {
        RegisterRequest validRequest = new RegisterRequest(
                "2007001@stud.kuet.ac.bd",
                "StrongPassword!2026",
                "Rahim Ahmed",
                "CSE",
                "2k20"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = beanValidator.validate(validRequest);
        assertTrue(violations.isEmpty());
    }
}

