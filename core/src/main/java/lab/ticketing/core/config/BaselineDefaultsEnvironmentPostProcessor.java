package lab.ticketing.core.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Adds shared defaults from lab/baseline-defaults.yml at the lowest precedence.
 *
 * <p>Loaded through {@code spring.config.import}, the imported file would take precedence over the
 * importing application.yml, so apps could not override its values. As these are defaults, they go last (addLast).
 * To change a single value for one app, set it in that app's application.yml (e.g. the app-worker pool size).
 */
public class BaselineDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String SOURCE_NAME = "lab-baseline-defaults";
    static final String LOCATION = "lab/baseline-defaults.yml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources sources = environment.getPropertySources();
        if (sources.contains(SOURCE_NAME)) {
            return;
        }
        ClassPathResource resource = new ClassPathResource(LOCATION, BaselineDefaultsEnvironmentPostProcessor.class.getClassLoader());
        try {
            List<PropertySource<?>> loaded = new YamlPropertySourceLoader().load(SOURCE_NAME, resource);
            loaded.forEach(sources::addLast);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load " + LOCATION, e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
