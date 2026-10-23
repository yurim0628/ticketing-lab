package lab.ticketing.testsupport.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/** Verifies the two-second Redis command timeout and the expected connection target. */
public final class RedisBaseline {

    public static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(2);

    private RedisBaseline() {
    }

    public static void assertApplied(ApplicationContext context, int expectedPort) {
        assertThat(context.getBeansOfType(LettuceConnectionFactory.class)).as("exactly one Redis connection").hasSize(1);
        LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
        LettuceClientConfiguration client = factory.getClientConfiguration();

        assertThat(client.getCommandTimeout()).as("spring.data.redis.timeout").isEqualTo(COMMAND_TIMEOUT);
        assertThat(client.getShutdownTimeout()).isEqualTo(Duration.ofMillis(200));
        assertThat(client.getClientOptions().orElseThrow().getSocketOptions().getConnectTimeout())
                .as("spring.data.redis.connect-timeout").isEqualTo(Duration.ofSeconds(2));
        assertThat(factory.getPort()).as("connected Redis port").isEqualTo(expectedPort);
        try (RedisConnection connection = factory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }
}
