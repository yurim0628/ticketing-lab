package lab.ticketing.core.security;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Wraps a secret. {@link #toString()} always returns a masked string, so the raw value does not leak
 * when a configuration object is logged or ends up in a startup failure message.
 * The raw value is available only through {@link #reveal()}.
 *
 * <p>Configuration binding converts values through the public String constructor.
 */
public final class Secret {

    private static final String MASK = "Secret[******]";

    private final String value;

    public Secret(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /** The raw value. Call it only where the value is actually needed, such as signing keys or DB passwords. */
    public String reveal() {
        return value;
    }

    public int byteLength() {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public String toString() {
        return MASK;
    }
}
