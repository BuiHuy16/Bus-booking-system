package com.example.busbooking.security;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class HmacVerificationCodeProviderTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void generatesSixDigitsAndBindsDigestToEmailAndSecret() {
        var provider = new HmacVerificationCodeProvider(key);
        assertThat(provider.generate()).matches("[0-9]{6}");
        String digest = provider.digest("hoang@example.com", "012345");
        assertThat(digest).matches("[a-f0-9]{64}");
        assertThat(provider.digest("hoang@example.com", "012345")).isEqualTo(digest);
        assertThat(provider.digest("other@example.com", "012345")).isNotEqualTo(digest);
        assertThat(provider.digest("hoang@example.com", "012346")).isNotEqualTo(digest);
        String other = Base64.getEncoder().encodeToString("another-32-byte-secret-for-tests!".getBytes());
        assertThat(new HmacVerificationCodeProvider(other).digest("hoang@example.com", "012345")).isNotEqualTo(digest);
    }

    @Test
    void rejectsMissingMalformedAndShortKeys() {
        for (String invalid : new String[] {null, "not base64", "", "dGVzdA=="}) {
            assertThatThrownBy(() -> new HmacVerificationCodeProvider(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
