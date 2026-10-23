package lab.ticketing.core.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Checks binding and validation only, without a DB. HikariDataSource does not connect until the first getConnection. */
class MysqlDataSourceConfigTest {

    private static final String PASSWORD = "pw-" + Long.toHexString(System.nanoTime());

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MysqlDataSourceConfig.class);

    @Test
    void buildsHikariFromValidatedPropertiesAndHikariSettings() {
        runner.withPropertyValues(
                        "lab.db.url=jdbc:mysql://localhost:1/ticketing",
                        "lab.db.username=booking",
                        "lab.db.password=" + PASSWORD,
                        "spring.datasource.hikari.maximum-pool-size=7")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    HikariDataSource ds = context.getBean(HikariDataSource.class);
                    assertThat(ds.getJdbcUrl()).isEqualTo("jdbc:mysql://localhost:1/ticketing");
                    assertThat(ds.getUsername()).isEqualTo("booking");
                    assertThat(ds.getPassword()).isEqualTo(PASSWORD);
                    assertThat(ds.getMaximumPoolSize()).isEqualTo(7);
                    assertThat(context.getBean(DbProperties.class).toString()).doesNotContain(PASSWORD);
                });
    }

    @Test
    void missingPasswordFailsStartup() {
        runner.withPropertyValues("lab.db.url=jdbc:mysql://localhost:1/ticketing", "lab.db.username=booking")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause().isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("lab.db.password");
                });
    }

    @Test
    void unresolvedPasswordPlaceholderFailsStartup() {
        runner.withPropertyValues(
                        "lab.db.url=jdbc:mysql://localhost:1/ticketing",
                        "lab.db.username=booking",
                        "lab.db.password=${NO_SUCH_ENV_FOR_TEST}")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("lab.db.password"));
    }

    @Test
    void rootAccountIsRejected() {
        runner.withPropertyValues(
                        "lab.db.url=jdbc:mysql://localhost:1/ticketing",
                        "lab.db.username=root",
                        "lab.db.password=" + PASSWORD)
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("lab.db.username")
                        .hasMessageNotContaining(PASSWORD));
    }
}
