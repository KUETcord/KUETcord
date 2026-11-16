package com.kuetcord.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends institutional verification emails via Spring JavaMailSender (Mailpit in local dev).
 * Gracefully degrades to structured logging when SMTP transport is disabled in test profiles.
 */
@Slf4j
@Service
public class EmailDispatchService {

    private final JavaMailSender mailSender;
    private final String frontendUrl;

    public EmailDispatchService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${kuetcord.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.frontendUrl = frontendUrl;
    }

    public void sendVerificationEmail(String recipientEmail, String displayName, String rawToken) {
        String verificationLink = frontendUrl + "/verify-email?token=" + rawToken;

        if (mailSender == null) {
            log.info("SMTP transport unavailable; verification token issued for {}", recipientEmail);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@kuetcord.local");
            message.setTo(recipientEmail);
            message.setSubject("Verify your KUETcord institutional email");
            message.setText("""
                    Hello %s,

                    Welcome to KUETcord! Please verify your @stud.kuet.ac.bd email address using the link below (valid for 15 minutes):

                    %s

                    If you did not create a KUETcord account, you can safely ignore this message.
                    """.formatted(displayName, verificationLink));
            mailSender.send(message);
        } catch (MailException ex) {
            // Do not fail registration transaction if local Mailpit isn't running during tests
            log.warn("Could not deliver verification email to {}: {}", recipientEmail, ex.getMessage());
        }
    }
}

