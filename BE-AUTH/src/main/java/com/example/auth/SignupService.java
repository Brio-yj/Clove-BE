package com.example.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupService {
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void signup(String email) {
        User user = new User(email);
        userRepository.save(user);
        eventPublisher.publishEvent(new UserSignedUpEvent(user.getEmail()));
    }
}
