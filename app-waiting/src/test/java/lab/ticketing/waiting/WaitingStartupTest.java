package lab.ticketing.waiting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import lab.ticketing.testsupport.TestSecrets;
import lab.ticketing.testsupport.startup.Startup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.NestedExceptionUtils;

/**
 * Missing secrets must fail startup. These tests run without Redis: validation finishes before connecting,
 * and the successful-startup control case needs no Redis either, because Lettuce does not connect until the first command.
 */
@ExtendWith(OutputCaptureExtension.class)
class WaitingStartupTest {

    private static final String WAIT_SECRET = TestSecrets.random(32);
    private static final String ADMIN_KEY = TestSecrets.random(24);

    @BeforeAll
    static void testJvmCarriesNoSecrets() {
        // The root build.gradle removes the .env.example keys from the test JVM environment, so 'missing' really means missing.
        assertThat(System.getenv("WAIT_SECRET")).isNull();
        assertThat(System.getenv("QUEUE_ADMIN_KEY")).isNull();
    }

    private static List<String> args(String profiles, String... extra) {
        List<String> args = new ArrayList<>(List.of("--spring.profiles.active=" + profiles, "--server.port=0",
                "--spring.main.banner-mode=off",
                // Unreachable Redis: proves startup needs no Redis, and never touches a Redis some other local stack runs on 6379.
                "--QUEUE_REDIS_HOST=127.0.0.1", "--QUEUE_REDIS_PORT=1"));
        args.addAll(List.of(extra));
        return args;
    }

    @Test
    void apiStartsWhenEverySecretIsPresent() {
        Startup.startsAndStops(WaitingApplication.class,
                args("api", "--WAIT_SECRET=" + WAIT_SECRET, "--QUEUE_ADMIN_KEY=" + ADMIN_KEY).toArray(String[]::new));
    }

    @ParameterizedTest(name = "{0} missing → startup fails")
    @ValueSource(strings = {"WAIT_SECRET", "QUEUE_ADMIN_KEY"})
    void apiFailsWhenOneSecretIsMissing(String missing, CapturedOutput output) {
        List<String> args = args("api");
        if (!missing.equals("WAIT_SECRET")) {
            args.add("--WAIT_SECRET=" + WAIT_SECRET);
        }
        if (!missing.equals("QUEUE_ADMIN_KEY")) {
            args.add("--QUEUE_ADMIN_KEY=" + ADMIN_KEY);
        }
        String property = missing.equals("WAIT_SECRET") ? "lab.waiting.secrets.waitSecret" : "lab.waiting.secrets.adminKey";

        Throwable failure = Startup.failure(WaitingApplication.class, args.toArray(String[]::new));

        assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining(property)
                .hasMessageContaining("required secret is not set");
        // The failure report (log) does not print the raw value of the other secret.
        assertThat(output.getAll()).contains("APPLICATION FAILED TO START").contains(property)
                .doesNotContain(WAIT_SECRET).doesNotContain(ADMIN_KEY);
    }

    @Test
    void apiFailsWhenSecretIsBlank() {
        Throwable failure = Startup.failure(WaitingApplication.class,
                args("api", "--WAIT_SECRET=", "--QUEUE_ADMIN_KEY=" + ADMIN_KEY).toArray(String[]::new));

        assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("lab.waiting.secrets.waitSecret")
                .hasMessageContaining("required secret is blank");
    }

    @Test
    void apiFailsWhenWaitSecretIsTooShortForHs256(CapturedOutput output) {
        String shortSecret = TestSecrets.random(12); // 16 chars < 32 bytes

        Throwable failure = Startup.failure(WaitingApplication.class,
                args("api", "--WAIT_SECRET=" + shortSecret, "--QUEUE_ADMIN_KEY=" + ADMIN_KEY).toArray(String[]::new));

        assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                .hasMessageContaining("lab.waiting.secrets.waitSecret")
                .hasMessageContaining("shorter than 32 bytes")
                .hasMessageNotContaining(shortSecret);
        assertThat(output.getAll()).doesNotContain(shortSecret).doesNotContain(ADMIN_KEY);
    }

    @Test
    void workerDoesNotNeedApiSecrets() {
        Startup.startsAndStops(WaitingApplication.class, args("worker").toArray(String[]::new));
    }

    @ParameterizedTest(name = "profile [{0}] → startup fails")
    @ValueSource(strings = {"default", "api,worker"})
    void refusesToStartWithoutExactlyOneRole(String profiles) {
        Throwable failure = Startup.failure(WaitingApplication.class,
                args(profiles, "--WAIT_SECRET=" + WAIT_SECRET, "--QUEUE_ADMIN_KEY=" + ADMIN_KEY).toArray(String[]::new));

        assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one profile");
    }
}
