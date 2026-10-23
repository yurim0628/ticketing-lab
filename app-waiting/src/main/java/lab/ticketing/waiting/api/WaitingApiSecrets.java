package lab.ticketing.waiting.api;

import lab.ticketing.core.config.RequiredSecret;
import lab.ticketing.core.security.Secret;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Required secrets for the api profile. They have no defaults, so startup fails if any one is missing.
 *
 * @param waitSecret reserved waiting API signing secret; at least 32 UTF-8 bytes
 * @param adminKey reserved administration API secret; at least 16 UTF-8 bytes
 */
@Validated
@ConfigurationProperties("lab.waiting.secrets")
public record WaitingApiSecrets(
        @RequiredSecret(minBytes = 32) Secret waitSecret,
        @RequiredSecret(minBytes = 16) Secret adminKey) {
}
