package lab.ticketing.booking;

import lab.ticketing.testsupport.containers.InfraProperties;
import lab.ticketing.testsupport.web.ThreadProbeController;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared booking IT setup: booking Redis + MySQL (Testcontainers). The configuration is identical, so all IT classes share a single context.
 * The MySQL account is lab_app, not root, and its password is generated at runtime.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability(tracing = false)
@Import(ThreadProbeController.class)
abstract class BookingITBase {

    @DynamicPropertySource
    static void infra(DynamicPropertyRegistry registry) {
        InfraProperties.bookingRedis(registry);
        InfraProperties.mysql(registry, "BOOKING");
    }
}
