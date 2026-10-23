package com.example.busbooking.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Set;

@ConfigurationProperties(prefix = "auth.rate-limit")
public class AuthRateLimitProperties {
    private long windowSeconds = 60;
    private int maxKeys = 10_000;
    private int registerLimit = 5;
    private int loginLimit = 10;
    private int verifyLimit = 20;
    private int resendLimit = 5;

    public long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(long value) { windowSeconds = value; }
    public int getMaxKeys() { return maxKeys; }
    public void setMaxKeys(int value) { maxKeys = value; }
    public int getRegisterLimit() { return registerLimit; }
    public void setRegisterLimit(int value) { registerLimit = value; }
    public int getLoginLimit() { return loginLimit; }
    public void setLoginLimit(int value) { loginLimit = value; }
    public int getVerifyLimit() { return verifyLimit; }
    public void setVerifyLimit(int value) { verifyLimit = value; }
    public int getResendLimit() { return resendLimit; }
    public void setResendLimit(int value) { resendLimit = value; }

    public int quota(String path) {
        return switch (path) {
            case "/api/auth/register" -> registerLimit;
            case "/api/auth/login" -> loginLimit;
            case "/api/auth/verify-email" -> verifyLimit;
            case "/api/auth/resend-verification" -> resendLimit;
            default -> 0;
        };
    }

    public void validate() {
        if (windowSeconds < 1 || windowSeconds > 86400 || maxKeys < 1 || maxKeys > 100_000
                || registerLimit < 1 || loginLimit < 1 || verifyLimit < 1 || resendLimit < 1
                || registerLimit > 1_000_000 || loginLimit > 1_000_000
                || verifyLimit > 1_000_000 || resendLimit > 1_000_000) {
            throw new IllegalStateException("Invalid auth rate limit configuration");
        }
    }
}
