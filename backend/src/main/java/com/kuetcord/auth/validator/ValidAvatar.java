package com.kuetcord.auth.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates free-tier safe user avatars: either a compact WebP/JPEG/PNG base64 Data URI
 * ({@code <= 24 KB} / 32,768 chars) or an {@code https://} image URL ({@code <= 500} chars).
 */
@Documented
@Constraint(validatedBy = AvatarValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAvatar {

    String message() default "Avatar must be a compact WebP/JPEG/PNG image (<= 24 KB) or a valid https:// image URL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
