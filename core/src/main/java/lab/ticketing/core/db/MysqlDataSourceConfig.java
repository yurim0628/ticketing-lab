package lab.ticketing.core.db;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pulled in with {@code @Import} by the apps that use MySQL (app-booking, app-worker).
 *
 * <p>Why this is built here instead of using Boot's default data source configuration: the connection settings
 * are validated through {@link DbProperties} first, so a missing password stops startup with a clear error before
 * any connection to the DB is attempted (before Flyway runs).
 * Pool settings still come from {@code spring.datasource.hikari.*} (shared defaults are in lab/baseline-defaults.yml).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DbProperties.class)
public class MysqlDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DbProperties db) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(db.url());
        dataSource.setUsername(db.username());
        dataSource.setPassword(db.password().reveal());
        return dataSource;
    }
}
