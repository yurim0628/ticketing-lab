package lab.ticketing.fakeext.walking;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("walking-skeleton")
class WalkingProviderController {

    @GetMapping("/__lab/provider/{requestId}")
    Response provider(@PathVariable UUID requestId) {
        return new Response(requestId, "fake-ext", "stub");
    }

    record Response(UUID requestId, String service, String mode) {
    }
}
