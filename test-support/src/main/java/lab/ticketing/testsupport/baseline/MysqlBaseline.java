package lab.ticketing.testsupport.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

/** Verifies the live connection pool, MySQL read limit and JDBC write timeout against shared defaults. */
public final class MysqlBaseline {

    public static final int POOL_SIZE = 30;
    public static final long CONNECTION_TIMEOUT_MS = 3000;
    public static final long READ_TIMEOUT_MS = 2000; // MySQL max_execution_time
    public static final int WRITE_TIMEOUT_S = 5; // JDBC query timeout

    private MysqlBaseline() {
    }

    public static void assertApplied(ApplicationContext context, int expectedPoolSize, String expectedPoolName) throws SQLException {
        HikariDataSource ds = context.getBean(HikariDataSource.class);
        assertThat(ds.getPoolName()).isEqualTo(expectedPoolName);
        assertThat(ds.getMaximumPoolSize()).as("hikari maximum-pool-size").isEqualTo(expectedPoolSize);
        assertThat(ds.getMinimumIdle()).as("hikari minimum-idle (= max)").isEqualTo(expectedPoolSize);
        assertThat(ds.getConnectionTimeout()).as("hikari connection-timeout").isEqualTo(CONNECTION_TIMEOUT_MS);
        assertThat(ds.getConnectionInitSql()).isEqualTo("SET SESSION max_execution_time = 2000");

        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.getQueryTimeout()).as("spring.jdbc.template.query-timeout").isEqualTo(WRITE_TIMEOUT_S);
        assertThat(jdbc.queryForObject("SELECT @@SESSION.max_execution_time", Long.class))
                .as("session max_execution_time").isEqualTo(READ_TIMEOUT_MS);
        AbstractPlatformTransactionManager tx = context.getBean(AbstractPlatformTransactionManager.class);
        assertThat(tx.getDefaultTimeout()).as("spring.transaction.default-timeout").isEqualTo(WRITE_TIMEOUT_S);
        assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view")).isEqualTo("false");

        try (Connection connection = ds.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            assertThat(meta.getDriverMajorVersion()).as("MySQL Connector/J 9.x or later (avoids pinning)").isGreaterThanOrEqualTo(9);
            assertThat(meta.getDatabaseProductVersion()).startsWith("8.4");
            assertThat(meta.getUserName()).doesNotStartWith("root@");
        }
    }
}
