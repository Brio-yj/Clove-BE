package com.example.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailRetryConsumer {
    private final JavaMailSender mailSender;

    @KafkaListener(topics = "email-retry", groupId = "auth-service")
    public void retry(String email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Signup Complete");
        message.setText("Welcome to Clove!");
        mailSender.send(message);
    }
}
