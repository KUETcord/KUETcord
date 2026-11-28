package com.kuetcord.auth.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class AvatarValidator implements ConstraintValidator<ValidAvatar, String> {

    public static final int MAX_DATA_URI_LENGTH = 32_768;
    public static final int MAX_HTTPS_URL_LENGTH = 500;

    private static final Pattern SAFE_DATA_URI = Pattern.compile(
            "^data:image/(webp|jpeg|png);base64,[A-Za-z0-9+/=]+$"
    );

    private static final Pattern SAFE_HTTPS_URL = Pattern.compile(
            "^https://[^\\s<>\"']+$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("data:")) {
            return trimmed.length() <= MAX_DATA_URI_LENGTH && SAFE_DATA_URI.matcher(trimmed).matches();
        }
        return trimmed.length() <= MAX_HTTPS_URL_LENGTH && SAFE_HTTPS_URL.matcher(trimmed).matches();
    }
}
