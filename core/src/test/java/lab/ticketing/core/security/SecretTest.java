package lab.ticketing.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretTest {

    @Test
    void toStringNeverShowsTheValue() {
        Secret secret = new Secret("value-that-must-not-leak");

        assertThat(secret.toString()).doesNotContain("value-that-must-not-leak").isEqualTo("Secret[******]");
        assertThat(String.valueOf(secret)).doesNotContain("leak");
        assertThat(secret.reveal()).isEqualTo("value-that-must-not-leak");
    }

    @Test
    void byteLengthIsUtf8() {
        assertThat(new Secret("abc").byteLength()).isEqualTo(3);
        assertThat(new Secret("\uAC00").byteLength()).isEqualTo(3); // U+AC00: one Hangul syllable, 3 bytes in UTF-8
    }
}
