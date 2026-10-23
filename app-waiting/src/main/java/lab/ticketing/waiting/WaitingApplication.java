package lab.ticketing.waiting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Waiting room application bootstrap. Select exactly one role for each process.
 * <ul>
 *   <li>{@code api}: loads and validates API secrets.</li>
 *   <li>{@code worker}: enables scheduled tasks without requiring API secrets.</li>
 * </ul>
 */
@SpringBootApplication
public class WaitingApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaitingApplication.class, args);
    }
}
