package lab.ticketing.archfixture.alpha;

import lab.ticketing.archfixture.beta.BetaService;

/** Deliberately violating fixture: app alpha calls app beta directly. Verifies that the checker catches it. */
public class AlphaService {

    public String call() {
        return new BetaService().name();
    }
}
