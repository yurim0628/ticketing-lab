package lab.ticketing.fakeext;

import lab.ticketing.testsupport.baseline.WebBaseline;
import lab.ticketing.testsupport.web.ActuatorChecks;
import lab.ticketing.testsupport.web.ThreadProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** Verifies bootstrap, shared web defaults and health for the simulator skeleton. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ThreadProbeController.class)
class FakeExtApplicationTest {

    @Autowired
    ApplicationContext context;

    @LocalServerPort
    int port;

    @Test
    void contextLoads() {
        WebBaseline.assertApplied(context, port);
        ActuatorChecks.assertHealthUp(port);
    }
}
