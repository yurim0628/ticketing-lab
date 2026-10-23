package lab.ticketing.core.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;
import lab.ticketing.core.security.Secret;

public class RequiredSecretValidator implements ConstraintValidator<RequiredSecret, Object> {

    private int minBytes;

    @Override
    public void initialize(RequiredSecret annotation) {
        this.minBytes = annotation.minBytes();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        String raw = switch (value) {
            case null -> null;
            case Secret secret -> secret.reveal();
            case String text -> text;
            default -> throw new IllegalArgumentException(
                    "@RequiredSecret supports Secret or String, not " + value.getClass().getName());
        };
        String problem = problem(raw);
        if (problem == null) {
            return true;
        }
        // The message states only the cause, never the value. It avoids braces and dollar signs so it is not interpolated as a template.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(problem).addConstraintViolation();
        return false;
    }

    private String problem(String raw) {
        if (raw == null) {
            return "required secret is not set";
        }
        if (raw.isBlank()) {
            return "required secret is blank";
        }
        if (Placeholders.isUnresolved(raw)) {
            return "required secret is not set (unresolved placeholder: environment variable missing)";
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length < minBytes) {
            return "required secret is shorter than " + minBytes + " bytes";
        }
        return null;
    }
}
