package lab.ticketing.fakeext.walking;

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

class WalkingProviderControllerTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(MvcConfiguration.class, WalkingProviderController.class);

    @Test
    void returnsTheRequestIdAndStubIdentity() {
        UUID requestId = UUID.randomUUID();
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("walking-skeleton"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WalkingProviderController.class);
                    MockMvcBuilders.webAppContextSetup(context).build()
                            .perform(get("/__lab/provider/{requestId}", requestId.toString().toUpperCase(Locale.ROOT)))
                            .andExpect(status().isOk())
                            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                            .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                            .andExpect(jsonPath("$.service").value("fake-ext"))
                            .andExpect(jsonPath("$.mode").value("stub"));
                });
    }

    @Test
    void rejectsMalformedRequestIds() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("walking-skeleton"))
                .run(context -> MockMvcBuilders.webAppContextSetup(context).build()
                        .perform(get("/__lab/provider/not-a-uuid"))
                        .andExpect(status().isBadRequest()));
    }

    @Test
    void isAbsentWithoutTheWalkingProfile() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("default"))
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(WalkingProviderController.class);
                    MockMvcBuilders.webAppContextSetup(context).build()
                            .perform(get("/__lab/provider/{requestId}", UUID.randomUUID()))
                            .andExpect(status().isNotFound());
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfiguration {
    }
}
