package lab.ticketing.waiting.api;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("api")
@EnableConfigurationProperties(WaitingApiSecrets.class)
class ApiConfig {
}
