package com.example.busbooking.config;

import com.example.busbooking.business.port.notification.EmailVerificationSender;
import com.example.busbooking.business.port.repository.EmailVerificationRepository;
import com.example.busbooking.business.port.security.VerificationCodeProvider;
import com.example.busbooking.business.service.EmailVerificationService;
import com.example.busbooking.notification.SmtpEmailVerificationSender;
import com.example.busbooking.security.HmacVerificationCodeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Clock;

@Configuration
public class EmailVerificationConfig {
    @Bean
    public VerificationCodeProvider verificationCodeProvider(@Value("${auth.email-otp-secret}") String secret) {
        return new HmacVerificationCodeProvider(secret);
    }

    @Bean
    public EmailVerificationSender emailVerificationSender(JavaMailSender sender,
                                                           @Value("${auth.mail-from}") String from) {
        return new SmtpEmailVerificationSender(sender, from);
    }

    @Bean
    public EmailVerificationService emailVerificationService(EmailVerificationRepository repository,
            VerificationCodeProvider codes, EmailVerificationSender sender, Clock clock) {
        return new EmailVerificationService(repository, codes, sender, clock);
    }
}
