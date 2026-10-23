package lab.ticketing.booking;

import lab.ticketing.core.db.MysqlDataSourceConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Booking application bootstrap with its own Redis connection and MySQL data source.
 * Flyway applies the bundled migrations at startup; queue Redis is configured by app-waiting.
 */
@SpringBootApplication
@Import(MysqlDataSourceConfig.class)
public class BookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }
}
