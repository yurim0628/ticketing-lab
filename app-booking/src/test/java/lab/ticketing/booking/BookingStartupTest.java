package lab.ticketing.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import lab.ticketing.testsupport.TestSecrets;
import lab.ticketing.testsupport.startup.Startup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.NestedExceptionUtils;

/**
 * Missing secrets must fail startup before opening a MySQL connection or running Flyway.
 * Also checks that the cause of the failure is binding validation, not a connection error.
 */
@ExtendWith(OutputCaptureExtension.class)
class BookingStartupTest {

    private static final String PASSWORD = TestSecrets.random(18);

    @BeforeAll
    static void testJvmCarriesNoSecrets() {
        assertThat(System.getenv("BOOKING_DB_PASSWORD")).isNull();
        assertThat(System.getenv("BOOKING_DB_USERNAME")).isNull();
    }

    private static String[] args(String... extra) {
        List<String> args = new ArrayList<>(List.of("--server.port=0", "--spring.main.banner-mode=off",
                // Unreachable address. If validation does not stop startup first, it fails with a connection error and the assertions below break.
                "--BOOKING_DB_URL=jdbc:mysql://127.0.0.1:1/ticketing"));
        args.addAll(List.of(extra));
        return args.toArray(String[]::new);
    }

    @Test
    void failsWithoutDbPasswordBeforeTouchingTheDatabase(CapturedOutput output) {
        Throwable failure = Startup.failure(BookingApplication.class, args("--BOOKING_DB_USERNAME=booking"));

        assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("lab.db.password")
                .hasMessageContaining("required secret is not set");
        assertThat(output.getAll()).doesNotContain("Communications link failure").doesNotContain("Flyway");
    }

    @Test
    void failsWithoutDbUsername() {
        Throwable failure = Startup.failure(BookingApplication.class, args("--BOOKING_DB_PASSWORD=" + PASSWORD));

        assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("lab.db.username")
                .hasMessageNotContaining(PASSWORD);
    }

    @Test
    void refusesRootAccount(CapturedOutput output) {
        Throwable failure = Startup.failure(BookingApplication.class,
                args("--BOOKING_DB_USERNAME=root", "--BOOKING_DB_PASSWORD=" + PASSWORD));

        assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("must not be the MySQL root account");
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }
}
