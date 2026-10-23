package lab.ticketing.testsupport.containers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * Registers container connection details under <b>environment variable names</b> (the keys in .env.example).
 * Because spring.* properties are not set directly, wiring such as {@code ${QUEUE_REDIS_HOST}} in application.yml is tested too.
 */
public final class InfraProperties {

    private InfraProperties() {
    }

    public static void queueRedis(DynamicPropertyRegistry registry) {
        redis(registry, "QUEUE", Containers.queueRedis());
    }

    public static void bookingRedis(DynamicPropertyRegistry registry) {
        redis(registry, "BOOKING", Containers.bookingRedis());
    }

    /** @param envPrefix BOOKING or WORKER → BOOKING_DB_URL, _USERNAME, _PASSWORD */
    public static void mysql(DynamicPropertyRegistry registry, String envPrefix) {
        MySQLContainer<?> c = Containers.mysql();
        registry.add(envPrefix + "_DB_URL", c::getJdbcUrl);
        registry.add(envPrefix + "_DB_USERNAME", c::getUsername);
        registry.add(envPrefix + "_DB_PASSWORD", c::getPassword);
    }

    private static void redis(DynamicPropertyRegistry registry, String envPrefix, GenericContainer<?> c) {
        registry.add(envPrefix + "_REDIS_HOST", c::getHost);
        registry.add(envPrefix + "_REDIS_PORT", () -> c.getMappedPort(Containers.REDIS_PORT));
    }
}
