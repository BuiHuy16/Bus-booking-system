package com.example.busbooking.security;

import com.example.busbooking.exception.ApiErrorWriter;
import com.example.busbooking.security.ratelimit.*;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import java.time.Clock;
import java.util.*;

@Configuration
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class SecurityConfig {
    @Bean
    public AuthRequestRateLimiter authRequestRateLimiter(AuthRateLimitProperties properties, Clock clock) {
        return new AuthRequestRateLimiter(properties, clock);
    }

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, JwtService jwt, CurrentUserLoader users,
            ApiErrorWriter errors, AuthRequestRateLimiter limiter,
            @Value("${springdoc.api-docs.enabled:false}") boolean docsEnabled,
            @Value("${auth.cors.allowed-origins:}") List<String> allowedOrigins) throws Exception {
        var entryPoint = new JsonAuthenticationEntryPoint(errors);
        var deniedHandler = new JsonAccessDeniedHandler(errors);
        var jwtFilter = new JwtAuthenticationFilter(jwt, users, entryPoint);
        var rateFilter = new AuthRateLimitFilter(limiter, errors);
        var boundary = new ApiExceptionBoundaryFilter(errors);
        var corsSource = new UrlBasedCorsConfigurationSource();
        var cors = new CorsConfiguration();
        var origins = allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
        if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("CORS origins must use explicit origins without wildcards");
        }
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("Retry-After"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(600L);
        corsSource.registerCorsConfiguration("/api/**", cors);
        var corsFilter = new CorsFilter(corsSource);
        corsFilter.setCorsProcessor(new JsonCorsProcessor(errors));

        http.csrf(csrf -> csrf.disable())
                .cors(corsConfig -> corsConfig.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(true)
                        .securityContextRepository(new NullSecurityContextRepository()))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(access -> {
                    access.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    access.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                            "/api/auth/verify-email", "/api/auth/resend-verification").permitAll();
                    access.requestMatchers(HttpMethod.GET, "/api/auth/me").hasAnyRole("USER", "ADMIN", "SUPER_ADMIN");
                    access.requestMatchers(HttpMethod.GET, "/api/trips", "/api/trips/{id}").permitAll();
                    access.requestMatchers(HttpMethod.GET, "/api/users/me").hasAnyRole("USER", "ADMIN", "SUPER_ADMIN");
                    access.requestMatchers(HttpMethod.PUT, "/api/users/me").hasAnyRole("USER", "ADMIN", "SUPER_ADMIN");
                    access.requestMatchers(HttpMethod.GET, "/api/users/me/bookings").hasRole("USER");
                    access.requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("USER");
                    access.requestMatchers(HttpMethod.GET, "/api/bookings/{id}").hasAnyRole("USER", "ADMIN", "SUPER_ADMIN");
                    access.requestMatchers(HttpMethod.DELETE, "/api/bookings/{id}").hasAnyRole("USER", "ADMIN", "SUPER_ADMIN");
                    access.requestMatchers("/api/admin/users/{userId}/role").hasRole("SUPER_ADMIN");
                    access.requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN");
                    if (docsEnabled) {
                        access.requestMatchers(HttpMethod.GET, "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**").permitAll();
                    }
                    access.anyRequest().denyAll();
                })
                .addFilterAt(corsFilter, CorsFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(boundary, AuthRateLimitFilter.class);
        return http.build();
    }
}
