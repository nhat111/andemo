package com.example.andemo.config;

import com.example.andemo.entity.User;
import com.example.andemo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = new User(null, "admin", passwordEncoder.encode("123456"), "ADMIN");
            User user = new User(null, "user", passwordEncoder.encode("123456"), "USER");
            userRepository.save(admin);
            userRepository.save(user);
            System.out.println(">>> Seeded users: admin/123456 (ADMIN), user/123456 (USER)");
        }
    }
}
