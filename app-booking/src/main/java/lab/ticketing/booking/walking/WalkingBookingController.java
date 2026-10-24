package lab.ticketing.booking.walking;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
@Profile("walking-skeleton")
public class WalkingBookingController {

    private final RestClient provider;

    public WalkingBookingController(RestClient.Builder builder,
            @Value("${lab.walking-skeleton.provider-url}") String providerUrl) {
        URI endpoint = URI.create(providerUrl);
        if (endpoint.getHost() == null
                || !("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))) {
            throw new IllegalArgumentException("lab.walking-skeleton.provider-url must be an absolute HTTP(S) URL");
        }
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(2));
        requests.setReadTimeout(Duration.ofSeconds(2));
        provider = builder.baseUrl(providerUrl).requestFactory(requests).build();
    }

    @GetMapping("/__lab/booking/{requestId}")
    public BookingProbe probe(@PathVariable UUID requestId) {
        ProviderProbe dependency;
        try {
            dependency = provider.get().uri("/__lab/provider/{requestId}", requestId)
                    .retrieve().body(ProviderProbe.class);
        } catch (RestClientException failure) {
            throw new ProviderUnavailableException(failure);
        }
        if (dependency == null || !requestId.equals(dependency.requestId())
                || !"fake-ext".equals(dependency.service()) || !"stub".equals(dependency.mode())) {
            throw new ProviderUnavailableException(null);
        }
        return new BookingProbe(requestId, "booking", "stub", dependency);
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<ProblemDetail> providerUnavailable() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The provider probe could not be completed.");
        problem.setTitle("Walking-skeleton provider unavailable");
        problem.setProperty("code", "walking_skeleton_provider_unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore()).body(problem);
    }

    public record BookingProbe(UUID requestId, String service, String mode, ProviderProbe dependency) {
    }

    public record ProviderProbe(UUID requestId, String service, String mode) {
    }

    private static final class ProviderUnavailableException extends RuntimeException {

        private ProviderUnavailableException(Throwable cause) {
            super("The provider probe is unavailable", cause);
        }
    }
}
