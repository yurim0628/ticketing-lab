package lab.ticketing.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import lab.ticketing.core.security.Secret;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequiredConstraintsTest {

    record Sample(@RequiredSecret(minBytes = 32) Secret key, @RequiredValue String name) {
    }

    record StringSecret(@RequiredSecret(minBytes = 4) String key) {
    }

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static final String GOOD_KEY = "k".repeat(32);

    @Test
    void acceptsPresentValues() {
        assertThat(validator.validate(new Sample(new Secret(GOOD_KEY), "booking"))).isEmpty();
        assertThat(validator.validate(new StringSecret("abcd"))).isEmpty();
    }

    @Test
    void missingSecretIsRejected() {
        assertThat(messages(validator.validate(new Sample(null, "x")))).containsExactly("required secret is not set");
    }

    @Test
    void blankSecretIsRejected() {
        assertThat(messages(validator.validate(new Sample(new Secret("   "), "x")))).containsExactly("required secret is blank");
    }

    @Test
    void unresolvedPlaceholderSecretIsRejected() {
        // The value Boot injects when the environment variable behind ${WAIT_SECRET} in application.yml is missing
        Set<ConstraintViolation<Sample>> violations = validator.validate(new Sample(new Secret("${WAIT_SECRET}"), "x"));
        assertThat(messages(violations)).singleElement().asString().contains("unresolved placeholder");
    }

    @Test
    void shortSecretIsRejectedWithoutEchoingTheValue() {
        String shortKey = "short-but-secret";
        Set<ConstraintViolation<Sample>> violations = validator.validate(new Sample(new Secret(shortKey), "x"));

        assertThat(messages(violations)).containsExactly("required secret is shorter than 32 bytes");
        ConstraintViolation<Sample> violation = violations.iterator().next();
        assertThat(violation.getMessage()).doesNotContain(shortKey);
        assertThat(String.valueOf(violation.getInvalidValue())).doesNotContain(shortKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "${BOOKING_DB_USERNAME}"})
    void requiredValueRejectsBlankAndUnresolved(String value) {
        assertThat(validator.validate(new Sample(new Secret(GOOD_KEY), value))).hasSize(1);
    }

    @Test
    void requiredValueRejectsNull() {
        assertThat(validator.validate(new Sample(new Secret(GOOD_KEY), null))).hasSize(1);
    }

    private static List<String> messages(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).toList();
    }
}
