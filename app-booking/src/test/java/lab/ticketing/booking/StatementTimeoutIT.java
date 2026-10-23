package lab.ticketing.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/**
 * Verifies that the two-second read limit and five-second write timeout interrupt real statements.
 * Reads use MySQL max_execution_time (the server aborts them); writes use the JDBC query timeout (Connector/J issues KILL QUERY).
 */
class StatementTimeoutIT extends BookingITBase {

    private static final int ER_QUERY_TIMEOUT = 3024; // maximum statement execution time exceeded

    @Autowired
    JdbcTemplate jdbc;

    private long sessionId;

    @BeforeEach
    void oneRow() {
        // The generated key comes back with the INSERT itself. A separate SELECT LAST_INSERT_ID() could run on another
        // pooled connection, return 0, match no row, skip SLEEP() and make the timeout tests below fail for the wrong reason.
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> con.prepareStatement(
                "INSERT INTO bk_ticket_session (name, starts_at, capacity, remaining) VALUES ('timeout-probe', NOW(3), 1, 1)",
                new String[] {"id"}), keys);
        sessionId = keys.getKey().longValue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bk_ticket_session WHERE id = ?", Long.class, sessionId)).isEqualTo(1);
    }

    @Test
    void slowSelectIsCutAtTwoSeconds() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM bk_ticket_session WHERE id = ? AND SLEEP(4) = 0", Long.class, sessionId))
                .satisfies(e -> {
                    Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
                    assertThat(cause).isInstanceOf(SQLException.class);
                    assertThat(((SQLException) cause).getErrorCode()).isEqualTo(ER_QUERY_TIMEOUT);
                });
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).isBetween(Duration.ofMillis(1800), Duration.ofMillis(3500));
    }

    @Test
    void slowWriteIsCutAtFiveSeconds() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE bk_ticket_session SET name = name WHERE id = ? AND SLEEP(8) = 0", sessionId))
                .isInstanceOf(QueryTimeoutException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e)).isInstanceOf(SQLTimeoutException.class));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).isBetween(Duration.ofMillis(4800), Duration.ofMillis(7000));
    }
}
