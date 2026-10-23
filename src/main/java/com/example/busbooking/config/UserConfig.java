package com.example.busbooking.config;

import com.example.busbooking.business.command.RegisterCommand;
import com.example.busbooking.business.port.repository.*;
import com.example.busbooking.business.port.security.PasswordHasher;
import com.example.busbooking.business.service.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(BootstrapSuperAdminProperties.class)
public class UserConfig {
    @Bean public UserService userService(UserRepository users) { return new UserService(users); }
    @Bean public RoleManagementService roleManagementService(UserRepository users, RoleManagementRepository roles) {
        return new RoleManagementService(users, roles);
    }
    @Bean public BootstrapSuperAdminService bootstrapSuperAdminService(UserRepository users, RoleRepository roles,
            PasswordHasher passwords, EmailVerificationService verification) {
        return new BootstrapSuperAdminService(users, roles, passwords, verification);
    }

    @Bean
    public ApplicationRunner superAdminBootstrapRunner(BootstrapSuperAdminProperties properties,
            Environment environment, BootstrapSuperAdminService service) {
        return arguments -> {
            if (!properties.isEnabled()) { return; }
            properties.requireAllowedProfiles(environment.getActiveProfiles());
            var result = service.bootstrap(new RegisterCommand(properties.getFullName(), properties.getEmail(),
                    properties.getPassword(), properties.getPhone()));
            var logger = LoggerFactory.getLogger(UserConfig.class);
            if (result == BootstrapSuperAdminService.Result.CREATED_EMAIL_PENDING) {
                logger.warn("Super admin is pending email verification; request another code after the resend cooldown");
            } else {
                logger.info("Super admin bootstrap completed: {}. Email verification is required before login", result);
            }
        };
    }
}
