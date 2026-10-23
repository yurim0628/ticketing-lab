package lab.ticketing.booking;

import static org.assertj.core.api.Assertions.assertThat;

import lab.ticketing.testsupport.baseline.MysqlBaseline;
import lab.ticketing.testsupport.baseline.RedisBaseline;
import lab.ticketing.testsupport.baseline.WebBaseline;
import lab.ticketing.testsupport.containers.Containers;
import lab.ticketing.testsupport.web.ActuatorChecks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

/** Verifies the booking context against booking Redis and MySQL containers. */
class BookingContextLoadIT extends BookingITBase {

    @Autowired
    ApplicationContext context;

    @Autowired
    HealthEndpoint health;

    @LocalServerPort
    int port;

    @Test
    void baselineWeb() {
        WebBaseline.assertApplied(context, port);
    }

    @Test
    void baselineRedisOnBookingRedis() {
        RedisBaseline.assertApplied(context, Containers.bookingRedis().getMappedPort(Containers.REDIS_PORT));
    }

    @Test
    void baselineMysql() throws Exception {
        MysqlBaseline.assertApplied(context, MysqlBaseline.POOL_SIZE, "booking");
    }

    @Test
    void healthIncludesDbAndRedis() {
        ActuatorChecks.assertHealthUp(port);
        assertThat(health.healthForPath("db").getStatus()).isEqualTo(Status.UP);
        assertThat(health.healthForPath("redis").getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void prometheusExposesHikariPool() {
        String body = ActuatorChecks.prometheus(port, "app-booking");
        assertThat(ActuatorChecks.metric(body, "hikaricp_connections_max", "pool=\"booking\"")).contains(30.0);
        assertThat(ActuatorChecks.metric(body, "hikaricp_connections_min", "pool=\"booking\"")).contains(30.0);
    }
}
