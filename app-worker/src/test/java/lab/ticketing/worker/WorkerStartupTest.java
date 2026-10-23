package lab.ticketing.worker;

import static org.assertj.core.api.Assertions.assertThat;

import lab.ticketing.testsupport.TestSecrets;
import lab.ticketing.testsupport.startup.Startup;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.core.NestedExceptionUtils;

/** Verifies startup rejection of missing or invalid secrets without MySQL or Redis. */
class WorkerStartupTest {

    @Test
    void failsWithoutDbPassword() {
        assertThat(System.getenv("WORKER_DB_PASSWORD")).isNull();

        Throwable failure = Startup.failure(WorkerApplication.class,
                "--server.port=0", "--spring.main.banner-mode=off",
                "--WORKER_DB_URL=jdbc:mysql://127.0.0.1:1/ticketing", "--WORKER_DB_USERNAME=worker");

        assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("lab.db.password")
                .hasMessageContaining("required secret is not set");
    }

    @Test
    void failsWithShortDbPasswordWithoutPrintingIt() {
        String shortPassword = TestSecrets.random(3); // 4 chars < 8 bytes

        Throwable failure = Startup.failure(WorkerApplication.class,
                "--server.port=0", "--spring.main.banner-mode=off",
                "--WORKER_DB_URL=jdbc:mysql://127.0.0.1:1/ticketing",
                "--WORKER_DB_USERNAME=worker", "--WORKER_DB_PASSWORD=" + shortPassword);

        assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                .hasMessageContaining("shorter than 8 bytes")
                .hasMessageNotContaining(shortPassword);
    }
}
