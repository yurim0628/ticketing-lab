package lab.ticketing.worker;

import static org.assertj.core.api.Assertions.assertThat;

import lab.ticketing.testsupport.baseline.MysqlBaseline;
import lab.ticketing.testsupport.baseline.RedisBaseline;
import lab.ticketing.testsupport.baseline.WebBaseline;
import lab.ticketing.testsupport.containers.Containers;
import lab.ticketing.testsupport.containers.InfraProperties;
import lab.ticketing.testsupport.web.ActuatorChecks;
import lab.ticketing.testsupport.web.ThreadProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Verifies the worker context against booking Redis and MySQL, without running migrations. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability(tracing = false)
@Import(ThreadProbeController.class)
class WorkerContextLoadIT {

    @DynamicPropertySource
    static void infra(DynamicPropertyRegistry registry) {
        InfraProperties.bookingRedis(registry);
        InfraProperties.mysql(registry, "WORKER");
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    HealthEndpoint health;

    @Autowired
    JdbcTemplate jdbc;

    @LocalServerPort
    int port;

    @Test
    void baselineSettingsWithSmallerPool() throws Exception {
        WebBaseline.assertApplied(context, port);
        RedisBaseline.assertApplied(context, Containers.bookingRedis().getMappedPort(Containers.REDIS_PORT));
        // Overrides only the pool size to 5; timeout values retain the shared defaults.
        MysqlBaseline.assertApplied(context, 5, "worker");
    }

    @Test
    void schedulingOnAndNoMigrations() {
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).hasSize(1);
        assertThat(context.containsBean("flyway")).isFalse();
        Integer historyTables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'",
                Integer.class);
        assertThat(historyTables).isZero();
    }

    @Test
    void healthAndPrometheus() {
        ActuatorChecks.assertHealthUp(port);
        assertThat(health.healthForPath("db").getStatus()).isEqualTo(Status.UP);
        assertThat(health.healthForPath("redis").getStatus()).isEqualTo(Status.UP);
        String body = ActuatorChecks.prometheus(port, "app-worker");
        assertThat(ActuatorChecks.metric(body, "hikaricp_connections_max", "pool=\"worker\"")).contains(5.0);
    }
}
