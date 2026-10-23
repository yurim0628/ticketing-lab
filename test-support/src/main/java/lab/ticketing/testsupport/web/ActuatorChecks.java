package lab.ticketing.testsupport.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

/** Common actuator checks: health UP, prometheus exposure and metric values. */
public final class ActuatorChecks {

    private ActuatorChecks() {
    }

    public static void assertHealthUp(int port) {
        HttpResponse<String> health = Http.get(port, "/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
        assertThat(Http.get(port, "/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(Http.get(port, "/actuator/health/readiness").statusCode()).isEqualTo(200);
    }

    /** The /actuator/prometheus body. It must contain the default JVM metrics and the application tag. */
    public static String prometheus(int port, String applicationName) {
        HttpResponse<String> response = Http.get(port, "/actuator/prometheus");
        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("jvm_threads_live_threads").contains("application=\"" + applicationName + "\"");
        return body;
    }

    /** Finds the value of the first line in the prometheus body that matches the name and every label fragment. */
    public static Optional<Double> metric(String prometheusBody, String name, String... labelFragments) {
        List<String> lines = prometheusBody.lines().filter(l -> l.startsWith(name + "{")).toList();
        return lines.stream()
                .filter(l -> List.of(labelFragments).stream().allMatch(l::contains))
                .findFirst()
                .map(l -> Double.parseDouble(l.substring(l.lastIndexOf(' ') + 1)));
    }
}
