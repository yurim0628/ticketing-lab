package lab.ticketing.testsupport.startup;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts an app directly inside a test. Values are passed as command-line arguments (--KEY=value).
 * Command-line arguments take precedence over environment variables, and the .env.example keys are cleared in the test JVM (root build.gradle).
 */
public final class Startup {

    private Startup() {
    }

    /** For startups that must fail. Returns the cause of the failure; fails the test if startup succeeds. */
    public static Throwable failure(Class<?> application, String... args) {
        ConfigurableApplicationContext context;
        try {
            context = new SpringApplication(application).run(args);
        } catch (RuntimeException e) {
            return e;
        }
        context.close();
        throw new AssertionError("expected startup to fail, but " + application.getSimpleName() + " started");
    }

    /** For startups that must succeed. Closes the context right after startup. */
    public static void startsAndStops(Class<?> application, String... args) {
        try (ConfigurableApplicationContext context = new SpringApplication(application).run(args)) {
            if (!context.isActive()) {
                throw new AssertionError(application.getSimpleName() + " is not active after startup");
            }
        }
    }
}
