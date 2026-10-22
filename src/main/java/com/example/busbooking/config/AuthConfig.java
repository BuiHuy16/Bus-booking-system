package com.example.busbooking.config;

import com.example.busbooking.business.port.repository.RoleRepository;
import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.business.port.security.PasswordHasher;
import com.example.busbooking.business.port.security.TokenProvider;
import com.example.busbooking.business.service.AuthService;
import com.example.busbooking.business.service.EmailVerificationService;
import com.example.busbooking.security.CurrentUserLoader;
import com.example.busbooking.security.JwtProperties;
import com.example.busbooking.security.JwtService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfig {
    @Bean
    public JwtService jwtService(JwtProperties properties, Clock clock) {
        return new JwtService(properties, clock);
    }

    @Bean
    public CurrentUserLoader currentUserLoader(UserRepository users) { return new CurrentUserLoader(users); }

    @Bean
    public AuthService authService(UserRepository users, RoleRepository roles, PasswordHasher passwords,
                                   TokenProvider tokens, EmailVerificationService verification) {
        return new AuthService(users, roles, passwords, tokens, verification);
    }
}
