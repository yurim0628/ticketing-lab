package lab.ticketing.waiting.worker;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables scheduling only when the worker role is selected. */
@Configuration(proxyBeanMethods = false)
@Profile("worker")
@EnableScheduling
class WorkerConfig {
}
