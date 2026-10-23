package lab.ticketing.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.SpringFactoriesLoader;

class BaselineDefaultsEnvironmentPostProcessorTest {

    @Test
    void addsBaselineValuesAtLowestPrecedence() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("app", Map.of("spring.application.name", "probe")));

        new BaselineDefaultsEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("server.tomcat.max-connections")).isEqualTo("4000");
        assertThat(env.getProperty("server.tomcat.accept-count")).isEqualTo("200");
        assertThat(env.getProperty("spring.threads.virtual.enabled")).isEqualTo("true");
        assertThat(env.getProperty("spring.data.redis.timeout")).isEqualTo("2s");
        assertThat(env.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("30");
        assertThat(env.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo("30");
        assertThat(env.getProperty("spring.datasource.hikari.connection-timeout")).isEqualTo("3000");
        assertThat(env.getProperty("spring.datasource.hikari.connection-init-sql"))
                .isEqualTo("SET SESSION max_execution_time = 2000");
        assertThat(env.getProperty("spring.jdbc.template.query-timeout")).isEqualTo("5s");
        assertThat(env.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(env.getProperty("management.metrics.tags.application")).isEqualTo("probe");
        assertThat(env.getPropertySources().stream().reduce((a, b) -> b).orElseThrow().getName())
                .isEqualTo(BaselineDefaultsEnvironmentPostProcessor.SOURCE_NAME);
    }

    @Test
    void appValuesOverrideBaseline() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("app",
                Map.of("spring.datasource.hikari.maximum-pool-size", "5")));

        new BaselineDefaultsEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("5");
        assertThat(env.getProperty("spring.datasource.hikari.connection-timeout")).isEqualTo("3000");
    }

    @Test
    void isRegisteredInSpringFactories() throws Exception {
        List<String> registered = new ArrayList<>();
        Enumeration<URL> urls = getClass().getClassLoader().getResources(SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION);
        while (urls.hasMoreElements()) {
            Properties props = PropertiesLoaderUtils.loadProperties(new UrlResource(urls.nextElement()));
            String value = props.getProperty(EnvironmentPostProcessor.class.getName());
            if (value != null) {
                registered.addAll(List.of(value.split(",")));
            }
        }
        assertThat(registered).map(String::trim).contains(BaselineDefaultsEnvironmentPostProcessor.class.getName());
    }
}
