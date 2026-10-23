package lab.ticketing.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/** Verifies automatic Flyway migration and the initial schema's nonnegative inventory constraints. */
class FlywayMigrationIT extends BookingITBase {

    private static final int ER_CHECK_CONSTRAINT_VIOLATED = 3819;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void v1IsAppliedAutomaticallyOnStartup() {
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, description, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        assertThat(history).hasSize(1);
        // success is TINYINT(1), so Connector/J returns it as a Boolean.
        assertThat(history.getFirst())
                .containsEntry("version", "1")
                .containsEntry("description", "create bk ticket session")
                .containsEntry("success", true);

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'bk\\_%'",
                String.class);
        assertThat(tables).containsExactly("bk_ticket_session");
    }

    @Test
    void negativeRemainingIsRejectedOnInsert() {
        assertCheckViolation(() -> insertSession(10, -1), "ck_bk_ticket_session_remaining");
    }

    @Test
    void decrementBelowZeroIsRejectedAndRowIsUnchanged() {
        long id = insertSession(10, 10);

        assertCheckViolation(() -> jdbc.update("UPDATE bk_ticket_session SET remaining = remaining - 11 WHERE id = ?", id),
                "ck_bk_ticket_session_remaining");

        assertThat(jdbc.queryForObject("SELECT remaining FROM bk_ticket_session WHERE id = ?", Integer.class, id)).isEqualTo(10);
        assertThat(jdbc.update("UPDATE bk_ticket_session SET remaining = remaining - 10 WHERE id = ?", id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT remaining FROM bk_ticket_session WHERE id = ?", Integer.class, id)).isZero();
    }

    @Test
    void remainingCannotExceedCapacity() {
        assertCheckViolation(() -> insertSession(10, 11), "ck_bk_ticket_session_remaining_le_capacity");
    }

    private long insertSession(int capacity, int remaining) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO bk_ticket_session (name, starts_at, capacity, remaining) VALUES (?, ?, ?, ?)",
                    new String[] {"id"});
            ps.setString(1, "session-" + System.nanoTime());
            ps.setTimestamp(2, Timestamp.from(Instant.parse("2026-12-05T01:00:00Z")));
            ps.setInt(3, capacity);
            ps.setInt(4, remaining);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    private static void assertCheckViolation(Runnable statement, String constraint) {
        assertThatThrownBy(statement::run)
                .satisfies(e -> {
                    Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
                    assertThat(cause).isInstanceOf(SQLException.class);
                    assertThat(((SQLException) cause).getErrorCode()).isEqualTo(ER_CHECK_CONSTRAINT_VIOLATED);
                    assertThat(cause).hasMessageContaining("'" + constraint + "'");
                });
    }
}
