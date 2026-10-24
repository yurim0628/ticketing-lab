package lab.ticketing.waiting.walking;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("api & walking-skeleton")
class WalkingWaitingController {

    @GetMapping("/__lab/waiting/{requestId}")
    Response waiting(@PathVariable UUID requestId) {
        return new Response(requestId, "waiting", "stub");
    }

    record Response(UUID requestId, String service, String mode) {
    }
}
