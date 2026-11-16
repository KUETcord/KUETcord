package com.kuetcord.auth.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bean validation constraint enforcing that an email address belongs strictly
 * to the institutional KUET student domain (@stud.kuet.ac.bd).
 */
@Documented
@Constraint(validatedBy = KuetEmailValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface KuetEmail {

    String message() default "Email must be a valid KUET institutional address (@stud.kuet.ac.bd)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

