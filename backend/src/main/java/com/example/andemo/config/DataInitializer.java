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
            // admin ở trung tâm TP.HCM
            User admin = new User(null, "admin", passwordEncoder.encode("123456"), "ADMIN",
                    10.7769, 106.7009);

            // user cách admin khoảng ~1.5km (Quận 1 → gần đó)
            User user = new User(null, "user", passwordEncoder.encode("123456"), "USER",
                    10.7829, 106.7000);

            // thêm 1 user nữa ở xa hơn (~5km)
            User user2 = new User(null, "user2", passwordEncoder.encode("123456"), "USER",
                    10.8100, 106.7100);

            userRepository.save(admin);
            userRepository.save(user);
            userRepository.save(user2);

            System.out.println(">>> Seeded users with sample locations (HCM)");
            System.out.println(">>> admin / user / user2  |  password: 123456");
        }
    }
}
