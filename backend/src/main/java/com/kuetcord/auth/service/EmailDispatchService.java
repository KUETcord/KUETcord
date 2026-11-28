package com.kuetcord.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends institutional verification and CT Hub notification emails via Spring JavaMailSender
 * (Mailpit in local dev, Gmail/Brevo SMTP in production). Gracefully degrades to structured logging when SMTP transport is disabled.
 */
@Slf4j
@Service
public class EmailDispatchService {

    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String mailFrom;

    public EmailDispatchService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${kuetcord.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${kuetcord.mail.from:noreply@kuetcord.local}") String mailFrom) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.frontendUrl = frontendUrl;
        this.mailFrom = mailFrom;
    }

    public void sendVerificationEmail(String recipientEmail, String displayName, String rawToken) {
        String verificationLink = frontendUrl + "/verify-email?token=" + rawToken;
        String body = """
                Hello %s,

                Welcome to KUETcord! Please verify your KUET institutional email address using the link below (valid for 15 minutes):

                %s

                If you did not create a KUETcord account, you can safely ignore this message.
                """.formatted(displayName, verificationLink);

        sendEmail(recipientEmail, "Verify your KUETcord institutional email", body);
    }

    public void sendTeacherCtNotificationEmail(String teacherEmail, String teacherName, String subject, String details) {
        String ctHubLink = frontendUrl + "/ct-hub";
        String body = """
                Hello %s,

                %s

                Review and manage this Class Test in KUETcord CT Hub:
                %s
                """.formatted(teacherName, details, ctHubLink);

        sendEmail(teacherEmail, subject, body);
    }

    private void sendEmail(String recipientEmail, String subject, String body) {
        if (mailSender == null) {
            log.info("SMTP transport unavailable; email [{}] logged for {}", subject, recipientEmail);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Could not deliver email [{}] to {}: {}", subject, recipientEmail, ex.getMessage());
        }
    }
}
