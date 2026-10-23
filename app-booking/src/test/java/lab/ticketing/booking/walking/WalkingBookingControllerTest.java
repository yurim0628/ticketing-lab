package lab.ticketing.booking.walking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class WalkingBookingControllerTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String PATH = "/__lab/booking/" + REQUEST_ID;

    private final AtomicReference<String> response = new AtomicReference<>(reply(REQUEST_ID, "fake-ext", "stub"));
    private final AtomicReference<String> requestedPath = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final CountDownLatch delayedResponse = new CountDownLatch(1);
    private HttpServer provider;
    private ExecutorService providerThreads;
    private String providerUrl;
    private volatile int responseStatus = 200;
    private volatile boolean delay;

    @BeforeEach
    void startProvider() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        providerThreads = Executors.newVirtualThreadPerTaskExecutor();
        provider.setExecutor(providerThreads);
        provider.createContext("/", exchange -> {
            try {
                calls.incrementAndGet();
                requestedPath.set(exchange.getRequestURI().getPath());
                if (delay) {
                    delayedResponse.await(10, TimeUnit.SECONDS);
                }
                byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        provider.start();
        providerUrl = "http://127.0.0.1:" + provider.getAddress().getPort();
    }

    @AfterEach
    void stopProvider() {
        delayedResponse.countDown();
        provider.stop(0);
        providerThreads.shutdownNow();
    }

    @Test
    void responsePreservesCorrelationAcrossTheProviderRequest() {
        activeContext().run(context -> {
            mvc(context).perform(get(PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value(REQUEST_ID.toString()))
                    .andExpect(jsonPath("$.service").value("booking"))
                    .andExpect(jsonPath("$.mode").value("stub"))
                    .andExpect(jsonPath("$.dependency.requestId").value(REQUEST_ID.toString()))
                    .andExpect(jsonPath("$.dependency.service").value("fake-ext"))
                    .andExpect(jsonPath("$.dependency.mode").value("stub"));
            assertThat(requestedPath.get()).isEqualTo("/__lab/provider/" + REQUEST_ID);
            assertThat(calls).hasValue(1);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"requestId", "service", "mode", "malformed", "empty"})
    void invalidProviderResponsesFailWithoutReturningTheirContents(String invalid) {
        response.set(switch (invalid) {
            case "requestId" -> reply(UUID.randomUUID(), "fake-ext", "stub");
            case "service" -> reply(REQUEST_ID, "upstream-only-detail", "stub");
            case "mode" -> reply(REQUEST_ID, "fake-ext", "upstream-only-detail");
            case "malformed" -> "upstream-only-detail";
            default -> "";
        });

        activeContext().run(context -> assertUnavailable(mvc(context)));
        assertThat(calls).hasValue(1);
    }

    @Test
    void upstreamHttpFailureHasTheSameGenericResponse() {
        responseStatus = 503;
        response.set("upstream-only-detail");

        activeContext().run(context -> assertUnavailable(mvc(context)));
        assertThat(calls).hasValue(1);
    }

    @Test
    void connectionFailureHasTheSameGenericResponse() {
        provider.stop(0);

        activeContext().run(context -> assertUnavailable(mvc(context)));
        assertThat(calls).hasValue(0);
    }

    @Test
    void responseTimeoutEndsTheProbeBeforeTheProviderReplies() {
        delay = true;

        activeContext().run(context -> {
            long start = System.nanoTime();
            assertUnavailable(mvc(context));
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
            assertThat(calls).hasValue(1);
            assertThat(delayedResponse.getCount()).isEqualTo(1);
        });
    }

    @Test
    void invalidRequestIdDoesNotCallTheProvider() {
        activeContext().run(context -> mvc(context).perform(get("/__lab/booking/not-a-uuid"))
                .andExpect(status().isBadRequest()));
        assertThat(calls).hasValue(0);
    }

    @Test
    void routeAndProviderConfigurationAreAbsentWithoutTheProfile() {
        context().run(context -> {
            assertThat(context).doesNotHaveBean(WalkingBookingController.class);
            mvc(context).perform(get(PATH)).andExpect(status().isNotFound());
        });
        assertThat(calls).hasValue(0);
    }

    private WebApplicationContextRunner activeContext() {
        return context().withInitializer(context -> context.getEnvironment().setActiveProfiles("walking-skeleton"))
                .withPropertyValues("lab.walking-skeleton.provider-url=" + providerUrl);
    }

    private WebApplicationContextRunner context() {
        return new WebApplicationContextRunner()
                .withUserConfiguration(WebConfiguration.class, WalkingBookingController.class)
                .withBean(RestClient.Builder.class, RestClient::builder);
    }

    private static MockMvc mvc(org.springframework.web.context.WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private void assertUnavailable(MockMvc mvc) throws Exception {
        String body = mvc.perform(get(PATH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("walking_skeleton_provider_unavailable"))
                .andExpect(jsonPath("$.detail").value("The provider probe could not be completed."))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("upstream-only-detail", providerUrl, "Exception", "java.net");
    }

    private static String reply(UUID requestId, String service, String mode) {
        return "{\"requestId\":\"" + requestId + "\",\"service\":\"" + service + "\",\"mode\":\"" + mode + "\"}";
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    static class WebConfiguration {
    }
}
