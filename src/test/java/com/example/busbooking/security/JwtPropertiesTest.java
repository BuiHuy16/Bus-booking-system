package com.example.busbooking.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.util.Map;
import org.springframework.core.env.MapPropertySource;

import static com.example.busbooking.security.JwtTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class JwtPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void bindsAndCreatesServiceWithAValidSeparateKey() {
        runner.withPropertyValues("jwt.secret=" + SECRET, "jwt.expiration-ms=60000", "jwt.issuer=test-issuer")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(JwtService.class);
                    assertThat(context.getBean(JwtService.class).issue(1L).expiresInSeconds()).isEqualTo(60);
                    assertThat(context.getBean(JwtProperties.class).toString()).doesNotContain(SECRET);
                });
    }

    @Test
    void missingMalformedAndShortSecretsFailWithoutDisclosingTheirValues() {
        runner.run(context -> assertThat(context).hasFailed());
        for (String invalid : new String[] {"not-a-secret-base64-value!", "dG9vLXNob3J0"}) {
            runner.withPropertyValues("jwt.secret=" + invalid).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("JWT_SECRET")
                        .hasStackTraceContaining("IllegalStateException");
                assertThat(context.getStartupFailure().toString()).doesNotContain(invalid);
            });
        }
    }

    @Test
    void rejectsZeroNegativeOrFractionalSecondTtl() {
        for (String invalid : new String[] {"0", "-1000", "999", "1001"}) {
            runner.withPropertyValues("jwt.secret=" + SECRET, "jwt.expiration-ms=" + invalid)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasStackTraceContaining("JWT_EXPIRATION_MS");
                    });
        }
    }

    @Test
    void rejectsBlankOrPaddedIssuer() {
        for (String invalid : new String[] {"", " ", " busbooking "}) {
            runner.withPropertyValues("jwt.secret=" + SECRET)
                    .withInitializer(context -> context.getEnvironment().getPropertySources()
                            .addFirst(new MapPropertySource("raw-issuer", Map.of("jwt.issuer", invalid))))
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasStackTraceContaining("JWT_ISSUER");
                    });
        }
    }

    @Test
    void validDefaultsPreserveTheConfiguredDayLongTtl() {
        runner.withPropertyValues("jwt.secret=" + SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtService.class).issue(1L).expiresInSeconds()).isEqualTo(86400);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class Config {
        @Bean JwtService jwtService(JwtProperties properties) {
            return new JwtService(properties, Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        }
    }
}
