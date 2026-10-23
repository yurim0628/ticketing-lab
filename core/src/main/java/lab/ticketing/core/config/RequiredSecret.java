package lab.ticketing.core.config;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A required secret with no default. Apply it to a {@link lab.ticketing.core.security.Secret} or a String.
 * Startup fails if the value is missing, blank, an unresolved {@code ${...}}, or shorter than {@link #minBytes()}.
 * Failure messages never include the value.
 */
@Documented
@Constraint(validatedBy = RequiredSecretValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredSecret {

    String message() default "is a required secret";

    /** Minimum length in UTF-8 bytes. HS256 signing keys need at least 32 (RFC 7518 3.2). */
    int minBytes() default 16;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
