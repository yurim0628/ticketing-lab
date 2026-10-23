package lab.ticketing.core.config;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A required, non-secret setting with no default. Startup fails if it is missing, blank,
 * or left as a literal {@code ${...}} because the environment variable is not set.
 */
@Documented
@Constraint(validatedBy = RequiredValueValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredValue {

    String message() default "is required (missing, blank, or unresolved placeholder)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
