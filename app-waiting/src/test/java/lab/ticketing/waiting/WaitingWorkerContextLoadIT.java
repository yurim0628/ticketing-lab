package lab.ticketing.waiting;

import static org.assertj.core.api.Assertions.assertThat;

import lab.ticketing.testsupport.baseline.RedisBaseline;
import lab.ticketing.testsupport.baseline.WebBaseline;
import lab.ticketing.testsupport.containers.Containers;
import lab.ticketing.testsupport.containers.InfraProperties;
import lab.ticketing.testsupport.web.ActuatorChecks;
import lab.ticketing.testsupport.web.ThreadProbeController;
import lab.ticketing.waiting.api.WaitingApiSecrets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Verifies the waiting worker context against queue Redis, without API secrets. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability(tracing = false) // turns on metrics export, which is off by default in tests (/actuator/prometheus)
@ActiveProfiles("worker")
@Import(ThreadProbeController.class)
class WaitingWorkerContextLoadIT {

    @DynamicPropertySource
    static void infra(DynamicPropertyRegistry registry) {
        InfraProperties.queueRedis(registry);
    }

    @Autowired
    ApplicationContext context;

    @LocalServerPort
    int port;

    @Test
    void workerRoleHasSchedulingAndNoApiSecrets() {
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).hasSize(1);
        assertThat(context.getBeansOfType(WaitingApiSecrets.class)).isEmpty();
    }

    @Test
    void baselineSettingsAndQueueRedis() {
        WebBaseline.assertApplied(context, port);
        RedisBaseline.assertApplied(context, Containers.queueRedis().getMappedPort(Containers.REDIS_PORT));
    }

    @Test
    void actuatorHealthAndPrometheus() {
        ActuatorChecks.assertHealthUp(port);
        ActuatorChecks.prometheus(port, "app-waiting");
    }
}
