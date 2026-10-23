package lab.ticketing.worker;

import lab.ticketing.core.db.MysqlDataSourceConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Booking worker bootstrap with scheduling, booking Redis and a MySQL data source.
 * Business jobs and coordination between worker instances are not implemented yet.
 */
@SpringBootApplication
@EnableScheduling
@Import(MysqlDataSourceConfig.class)
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
