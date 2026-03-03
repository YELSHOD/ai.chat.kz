package com.yelshod.ai.chat.kz.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class AuthEmailService {

    private static final Logger log = LoggerFactory.getLogger(AuthEmailService.class);
    private static final DateTimeFormatter EXPIRES_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    public AuthEmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                            @Value("${app.mail.from:no-reply@ai.chat.kz}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
    }

    public boolean sendVerificationEmail(String toEmail, String username, String verifyLink, Instant expiresAt) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Verification email for {} was not sent.", toEmail);
            return false;
        }

        String displayName = (username == null || username.isBlank()) ? "there" : username;
        String expiresText = EXPIRES_FORMATTER.format(expiresAt.atZone(ZoneId.systemDefault()));
        String subject = "Confirm your email for ai.chat.kz";
        String body = """
                Hi %s,

                Thanks for registering in ai.chat.kz.
                Please confirm your email by opening this link:
                %s

                This link expires at: %s

                If you did not create this account, you can ignore this email.

                Best regards,
                ai.chat.kz team
                """.formatted(displayName, verifyLink, expiresText);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception exception) {
            log.error("Failed to send verification email to {}", toEmail, exception);
            return false;
        }
    }
}
