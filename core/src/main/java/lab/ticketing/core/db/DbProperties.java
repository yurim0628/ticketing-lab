package lab.ticketing.core.db;

import jakarta.validation.constraints.Pattern;
import lab.ticketing.core.config.RequiredSecret;
import lab.ticketing.core.config.RequiredValue;
import lab.ticketing.core.security.Secret;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MySQL connection settings. Each app's application.yml maps its own environment variables (BOOKING_DB_*, WORKER_DB_*) here.
 * The password and username have no defaults, and the root account is not allowed.
 */
@Validated
@ConfigurationProperties("lab.db")
public record DbProperties(
        @RequiredValue String url,
        @RequiredValue
        @Pattern(regexp = "^(?!root$).*$", message = "must not be the MySQL root account")
        String username,
        @RequiredSecret(minBytes = 8) Secret password) {
}
