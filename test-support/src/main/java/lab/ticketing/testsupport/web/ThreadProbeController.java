package lab.ticketing.testsupport.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Imported only by tests with {@code @Import}; reports whether the request thread is virtual.
 * It sits outside the apps' component-scan scope (lab.ticketing.&lt;app&gt;), so it never ends up in production code.
 */
@RestController
public class ThreadProbeController {

    public static final String PATH = "/__test/thread";

    @GetMapping(PATH)
    public Map<String, Object> thread() {
        Thread current = Thread.currentThread();
        return Map.of("virtual", current.isVirtual(), "name", current.getName());
    }
}
