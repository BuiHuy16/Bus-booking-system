package com.example.busbooking.notification;

import com.example.busbooking.business.port.notification.EmailVerificationSender;
import com.example.busbooking.domain.exception.EmailDeliveryException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Instant;

public final class SmtpEmailVerificationSender implements EmailVerificationSender {
    private final JavaMailSender sender;
    private final String from;

    public SmtpEmailVerificationSender(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String email, String code, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Xác minh email — Bus Booking");
        message.setText("Mã xác minh email của bạn: " + code
                + "\nMã có hiệu lực trong 10 phút, đến " + expiresAt
                + " (UTC).\nKhông chia sẻ mã này với người khác."
                + "\nNếu bạn không đăng ký, hãy bỏ qua email này.");
        try { sender.send(message); }
        catch (MailException failure) {
            // Do not propagate SMTP details that may contain addresses, credentials or message body.
            throw new EmailDeliveryException();
        }
    }
}
