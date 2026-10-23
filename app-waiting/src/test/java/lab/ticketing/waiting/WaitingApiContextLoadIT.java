package lab.ticketing.waiting;

import static org.assertj.core.api.Assertions.assertThat;

import lab.ticketing.testsupport.TestSecrets;
import lab.ticketing.testsupport.baseline.RedisBaseline;
import lab.ticketing.testsupport.baseline.WebBaseline;
import lab.ticketing.testsupport.containers.Containers;
import lab.ticketing.testsupport.containers.InfraProperties;
import lab.ticketing.testsupport.web.ActuatorChecks;
import lab.ticketing.testsupport.web.ThreadProbeController;
import lab.ticketing.waiting.api.WaitingApiSecrets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Verifies the waiting API context against its queue Redis container. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability(tracing = false) // turns on metrics export, which is off by default in tests (/actuator/prometheus)
@ActiveProfiles("api")
@Import(ThreadProbeController.class)
class WaitingApiContextLoadIT {

    private static final String WAIT_SECRET = TestSecrets.random(32);
    private static final String ADMIN_KEY = TestSecrets.random(24);

    @DynamicPropertySource
    static void infra(DynamicPropertyRegistry registry) {
        InfraProperties.queueRedis(registry);
        registry.add("WAIT_SECRET", () -> WAIT_SECRET);
        registry.add("QUEUE_ADMIN_KEY", () -> ADMIN_KEY);
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    HealthEndpoint health;

    @LocalServerPort
    int port;

    @Test
    void baselineWebSettingsReachTomcatAndRequestsRunOnVirtualThreads() {
        WebBaseline.assertApplied(context, port);
    }

    @Test
    void connectsToQueueRedisWithBaselineTimeout() {
        RedisBaseline.assertApplied(context, Containers.queueRedis().getMappedPort(Containers.REDIS_PORT));
        assertThat(health.healthForPath("redis").getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void secretsAreBoundButNeverPrinted() {
        WaitingApiSecrets secrets = context.getBean(WaitingApiSecrets.class);
        assertThat(secrets.waitSecret().reveal()).isEqualTo(WAIT_SECRET);
        assertThat(secrets.toString()).doesNotContain(WAIT_SECRET).doesNotContain(ADMIN_KEY);
    }

    @Test
    void actuatorHealthAndPrometheus() {
        ActuatorChecks.assertHealthUp(port);
        ActuatorChecks.prometheus(port, "app-waiting");
    }
}
