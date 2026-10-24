package lab.ticketing.waiting.walking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class WalkingWaitingControllerTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(MvcConfiguration.class, WalkingWaitingController.class);

    @Test
    void returnsTheRequestIdAndStubIdentity() {
        UUID requestId = UUID.randomUUID();
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("api", "walking-skeleton"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WalkingWaitingController.class);
                    MockMvcBuilders.webAppContextSetup(context).build()
                            .perform(get("/__lab/waiting/{requestId}", requestId.toString().toUpperCase(Locale.ROOT)))
                            .andExpect(status().isOk())
                            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                            .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                            .andExpect(jsonPath("$.service").value("waiting"))
                            .andExpect(jsonPath("$.mode").value("stub"));
                });
    }

    @Test
    void rejectsMalformedRequestIds() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("api", "walking-skeleton"))
                .run(context -> MockMvcBuilders.webAppContextSetup(context).build()
                        .perform(get("/__lab/waiting/not-a-uuid"))
                        .andExpect(status().isBadRequest()));
    }

    @Test
    void isAbsentFromTheNormalApiProfile() {
        assertUnavailable("api");
    }

    @Test
    void isAbsentFromTheWorkerWalkingProfile() {
        assertUnavailable("worker", "walking-skeleton");
    }

    @Test
    void requiresTheApiProfile() {
        assertUnavailable("walking-skeleton");
    }

    private void assertUnavailable(String... profiles) {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(WalkingWaitingController.class);
                    MockMvcBuilders.webAppContextSetup(context).build()
                            .perform(get("/__lab/waiting/{requestId}", UUID.randomUUID()))
                            .andExpect(status().isNotFound());
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfiguration {
    }
}
