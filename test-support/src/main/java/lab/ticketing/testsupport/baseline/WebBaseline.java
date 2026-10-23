package lab.ticketing.testsupport.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import lab.ticketing.testsupport.web.Http;
import lab.ticketing.testsupport.web.ThreadProbeController;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;

/**
 * Verifies shared web defaults on the <b>running Tomcat instance</b>, beyond configuration file values.
 * Virtual threads are verified directly on the request-handling thread via {@link ThreadProbeController}.
 */
public final class WebBaseline {

    public static final int MAX_CONNECTIONS = 4000;
    public static final int ACCEPT_COUNT = 200;
    public static final int KEEP_ALIVE_TIMEOUT_MS = 60_000;

    private WebBaseline() {
    }

    public static void assertApplied(ApplicationContext context, int port) {
        WebServer webServer = ((ServletWebServerApplicationContext) context).getWebServer();
        Connector connector = ((TomcatWebServer) webServer).getTomcat().getConnector();
        AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) connector.getProtocolHandler();

        assertThat(protocol.getMaxConnections()).as("server.tomcat.max-connections").isEqualTo(MAX_CONNECTIONS);
        assertThat(protocol.getAcceptCount()).as("server.tomcat.accept-count").isEqualTo(ACCEPT_COUNT);
        assertThat(protocol.getKeepAliveTimeout()).as("server.tomcat.keep-alive-timeout").isEqualTo(KEEP_ALIVE_TIMEOUT_MS);
        assertThat(protocol.getMaxKeepAliveRequests()).as("server.tomcat.max-keep-alive-requests").isEqualTo(-1);
        assertThat(protocol.getExecutor().getClass().getSimpleName()).as("Tomcat executor").isEqualTo("VirtualThreadExecutor");

        HttpResponse<String> probe = Http.get(port, ThreadProbeController.PATH);
        assertThat(probe.statusCode()).isEqualTo(200);
        assertThat(probe.body()).as("request thread").contains("\"virtual\":true");
    }
}
