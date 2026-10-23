package lab.ticketing.core.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RequiredValueValidator implements ConstraintValidator<RequiredValue, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && !value.isBlank() && !Placeholders.isUnresolved(value);
    }
}
