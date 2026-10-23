package lab.ticketing.fakeext;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap for a local external-service simulator.
 * The current app exposes health endpoints; simulation routes and fault injection are not implemented yet.
 */
@SpringBootApplication
public class FakeExtApplication {

    public static void main(String[] args) {
        SpringApplication.run(FakeExtApplication.class, args);
    }
}
