package lab.ticketing.testsupport.containers;

import lab.ticketing.testsupport.TestSecrets;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers singletons. Each JVM (= one Gradle module's test task) starts only one container of each kind.
 * Started on first use and removed by Testcontainers (Ryuk) afterwards. No fixed host ports are used.
 *
 * <p>Redis is split into two instances (queue and booking). Each app connects only to its own Redis.
 */
public final class Containers {

    public static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.4-alpine");
    public static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4");
    public static final int REDIS_PORT = 6379;

    private static final long REDIS_MEMORY = 128L * 1024 * 1024;
    private static final long MYSQL_MEMORY = 768L * 1024 * 1024;

    private Containers() {
    }

    public static GenericContainer<?> queueRedis() {
        return QueueRedis.INSTANCE;
    }

    public static GenericContainer<?> bookingRedis() {
        return BookingRedis.INSTANCE;
    }

    public static MySQLContainer<?> mysql() {
        return Mysql.INSTANCE;
    }

    @SuppressWarnings("resource")
    private static GenericContainer<?> redis(String role) {
        GenericContainer<?> c = new GenericContainer<>(REDIS_IMAGE)
                .withExposedPorts(REDIS_PORT)
                // Persist queue writes with AOF every second; reject writes when full instead of evicting entries.
                .withCommand("redis-server", "--appendonly", "yes", "--appendfsync", "everysec",
                        "--maxmemory-policy", "noeviction")
                .withLabel("lab.ticketing.role", role)
                .withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(cmd.getHostConfig().withMemory(REDIS_MEMORY)));
        c.start();
        return c;
    }

    private static final class QueueRedis {
        static final GenericContainer<?> INSTANCE = redis("redis-queue");
    }

    private static final class BookingRedis {
        static final GenericContainer<?> INSTANCE = redis("redis-booking");
    }

    private static final class Mysql {
        static final MySQLContainer<?> INSTANCE = start();

        @SuppressWarnings("resource")
        private static MySQLContainer<?> start() {
            MySQLContainer<?> c = new MySQLContainer<>(MYSQL_IMAGE)
                    .withDatabaseName("ticketing")
                    .withUsername("lab_app")
                    .withPassword(TestSecrets.random(18))
                    // Values chosen to save laptop Docker memory. Not production settings.
                    .withCommand("--innodb-buffer-pool-size=64M", "--performance-schema=OFF")
                    .withLabel("lab.ticketing.role", "mysql")
                    .withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(cmd.getHostConfig().withMemory(MYSQL_MEMORY)));
            c.start();
            return c;
        }
    }
}
