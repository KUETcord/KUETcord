package com.kuetcord.auth.validator;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates institutional student email addresses strictly belonging to KUET:
 * Format: &lt;student_id&gt;@stud.kuet.ac.bd
 * E.g., 2007001@stud.kuet.ac.bd
 */
@Component
public class EmailDomainValidator {

    public static final String REQUIRED_DOMAIN = "@stud.kuet.ac.bd";
    
    // Strict institutional student email pattern (alphanumeric username followed by exact domain)
    private static final Pattern KUET_STUDENT_EMAIL_PATTERN = 
            Pattern.compile("^[a-zA-Z0-9._%+-]+@stud\\.kuet\\.ac\\.bd$", Pattern.CASE_INSENSITIVE);

    /**
     * Verifies that the provided email string belongs strictly to the KUET student domain.
     * Rejects null, blank, external domains (gmail.com, yahoo.com), and subdomain attack attempts.
     *
     * @param email The email address to inspect.
     * @return true if valid institutional email; false otherwise.
     */
    public boolean isValid(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        
        // Disallow spaces or control characters
        if (normalized.contains(" ") || normalized.contains("\t") || normalized.contains("\n")) {
            return false;
        }

        // Must end strictly with @stud.kuet.ac.bd
        if (!normalized.endsWith(REQUIRED_DOMAIN)) {
            return false;
        }

        // Must pass regex pattern validation
        return KUET_STUDENT_EMAIL_PATTERN.matcher(normalized).matches();
    }
}

