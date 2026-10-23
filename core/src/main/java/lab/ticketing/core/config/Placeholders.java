package lab.ticketing.core.config;

/**
 * During {@code @ConfigurationProperties} binding, Spring Boot injects an unresolved {@code ${ENV_NAME}}
 * verbatim, without an error. A missing environment variable would leave the app using that literal string
 * as the value, so it is detected separately.
 */
final class Placeholders {

    private Placeholders() {
    }

    static boolean isUnresolved(String value) {
        return value.contains("${");
    }
}
