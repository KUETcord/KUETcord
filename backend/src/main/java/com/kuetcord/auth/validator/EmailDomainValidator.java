package com.kuetcord.auth.validator;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates institutional KUET email addresses for both students and faculty:
 * - Students: &lt;student_id&gt;@stud.kuet.ac.bd (e.g., 2007001@stud.kuet.ac.bd)
 * - Faculty:  &lt;name&gt;@kuet.ac.bd or &lt;name&gt;@&lt;dept&gt;.kuet.ac.bd (e.g., hasan@cse.kuet.ac.bd)
 */
@Component
public class EmailDomainValidator {

    public static final String STUDENT_DOMAIN = "@stud.kuet.ac.bd";

    private static final Pattern KUET_STUDENT_EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@stud\\.kuet\\.ac\\.bd$", Pattern.CASE_INSENSITIVE);

    private static final Pattern KUET_FACULTY_EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9-]+\\.)?kuet\\.ac\\.bd$", Pattern.CASE_INSENSITIVE);

    public boolean isValid(String email) {
        return isStudentEmail(email) || isFacultyEmail(email);
    }

    public boolean isStudentEmail(String email) {
        String normalized = normalize(email);
        if (normalized == null) {
            return false;
        }
        return normalized.endsWith(STUDENT_DOMAIN)
                && KUET_STUDENT_EMAIL_PATTERN.matcher(normalized).matches();
    }

    public boolean isFacultyEmail(String email) {
        String normalized = normalize(email);
        if (normalized == null || normalized.endsWith(STUDENT_DOMAIN)) {
            return false;
        }
        return KUET_FACULTY_EMAIL_PATTERN.matcher(normalized).matches();
    }

    private String normalize(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        if (trimmed.contains(" ") || trimmed.contains("\t") || trimmed.contains("\n")) {
            return null;
        }
        return trimmed;
    }
}
