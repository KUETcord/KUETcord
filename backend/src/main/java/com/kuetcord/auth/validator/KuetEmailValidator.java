package com.kuetcord.auth.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JSR-380 ConstraintValidator delegating institutional email verification
 * to {@link EmailDomainValidator}.
 */
@Component
public class KuetEmailValidator implements ConstraintValidator<KuetEmail, String> {

    private final EmailDomainValidator emailDomainValidator;

    /**
     * No-arg constructor for standalone Bean Validation factories outside Spring DI.
     */
    public KuetEmailValidator() {
        this.emailDomainValidator = new EmailDomainValidator();
    }

    @Autowired
    public KuetEmailValidator(EmailDomainValidator emailDomainValidator) {
        this.emailDomainValidator = emailDomainValidator;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            // @NotBlank handles nullability separately to avoid duplicate constraint messages
            return true;
        }
        return emailDomainValidator.isValid(value);
    }
}
