package lab.ticketing.testsupport;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates test secrets at runtime so that no secret strings are written in source (avoids gitleaks false positives and mistakes).
 * If a test needs to reuse the same value, obtain it once and keep it in a static final field.
 */
public final class TestSecrets {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestSecrets() {
    }

    /** A base64url string, longer than {@code bytes} (32 bytes → 43 characters). */
    public static String random(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
