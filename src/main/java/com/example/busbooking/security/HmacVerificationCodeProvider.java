package com.example.busbooking.security;

import com.example.busbooking.business.port.security.VerificationCodeProvider;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

public final class HmacVerificationCodeProvider implements VerificationCodeProvider {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public HmacVerificationCodeProvider(String base64Secret) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(base64Secret); }
        catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IllegalArgumentException("EMAIL_OTP_SECRET must be valid Base64");
        }
        if (decoded.length < 32) {
            throw new IllegalArgumentException("EMAIL_OTP_SECRET requires at least 32 decoded bytes");
        }
        key = new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Override
    public String generate() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    @Override
    public String digest(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal((email + "\n" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Cannot compute verification digest", failure);
        }
    }
}
