package com.example.busbooking.notification;

import com.example.busbooking.domain.exception.EmailDeliveryException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpEmailVerificationSenderTest {
    @Test
    void composesEmailWithOtpAndExpiry() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        var sender = new SmtpEmailVerificationSender(smtp, "noreply@example.com");
        sender.send("hoang@example.com", "012345", Instant.parse("2026-10-22T00:10:00Z"));
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("hoang@example.com");
        assertThat(message.getValue().getFrom()).isEqualTo("noreply@example.com");
        assertThat(message.getValue().getText()).contains("012345", "2026-10-22T00:10:00Z");
    }

    @Test
    void doesNotExposeSmtpDetailsOnFailure() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        doThrow(new MailSendException("sensitive SMTP details"))
                .when(smtp).send(any(SimpleMailMessage.class));
        var sender = new SmtpEmailVerificationSender(smtp, "noreply@example.com");
        assertThatThrownBy(() -> sender.send("hoang@example.com", "012345", Instant.now()))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageNotContaining("sensitive")
                .hasMessageNotContaining("012345")
                .hasNoCause();
    }
}
