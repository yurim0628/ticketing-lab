package lab.ticketing.waiting;

import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Exactly one profile: api or worker. Startup is refused if neither or both are active.
 * Separate processes allow API traffic and scheduled work to be scaled and restarted independently.
 */
@Component
class WaitingRoleGuard {

    static final String API = "api";
    static final String WORKER = "worker";

    WaitingRoleGuard(Environment environment) {
        boolean api = environment.matchesProfiles(API);
        boolean worker = environment.matchesProfiles(WORKER);
        if (api == worker) {
            throw new IllegalStateException("app-waiting needs exactly one profile: 'api' or 'worker' (active: "
                    + Arrays.toString(environment.getActiveProfiles()) + ")");
        }
    }
}
