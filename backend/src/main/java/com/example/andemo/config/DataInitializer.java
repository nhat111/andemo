package com.example.andemo.config;

import com.example.andemo.entity.CommonCode;
import com.example.andemo.entity.User;
import com.example.andemo.repository.CommonCodeRepository;
import com.example.andemo.repository.UserRepository;
import com.example.andemo.service.MenuLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CommonCodeRepository commonCodeRepository;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            // admin ở trung tâm TP.HCM
            User admin = new User(null, "admin", passwordEncoder.encode("123456"), "ADMIN",
                    10.7769, 106.7009, "STORE001");

            // user cách admin khoảng ~1.5km (Quận 1 → gần đó)
            User user = new User(null, "user", passwordEncoder.encode("123456"), "USER",
                    10.7829, 106.7000, "STORE001");

            // thêm 1 user nữa ở xa hơn (~5km)
            User user2 = new User(null, "user2", passwordEncoder.encode("123456"), "USER",
                    10.8100, 106.7100, "STORE002");

            userRepository.save(admin);
            userRepository.save(user);
            userRepository.save(user2);

            System.out.println(">>> Seeded users with sample locations (HCM)");
            System.out.println(">>> admin / user / user2  |  password: 123456");
        }

        // COMMON_CODE: danh sách menu có thể khóa
        // BLOCK = chặn vào màn hình, MASK = vào được nhưng ẩn Giá vốn / Tỷ suất lợi nhuận
        if (commonCodeRepository.countByGroupCode(MenuLockService.GROUP_LOCK_MENU) == 0) {
            String g = MenuLockService.GROUP_LOCK_MENU;
            commonCodeRepository.save(new CommonCode(null, g, "ORDER", "Đặt hàng", "BLOCK", 1, true));
            commonCodeRepository.save(new CommonCode(null, g, "RETURN", "Trả hàng", "BLOCK", 2, true));
            commonCodeRepository.save(new CommonCode(null, g, "DISPOSAL", "Hủy hàng", "BLOCK", 3, true));
            commonCodeRepository.save(new CommonCode(null, g, "INVENTORY", "Tồn kho", "BLOCK", 4, true));
            commonCodeRepository.save(new CommonCode(null, g, "COST", "Giá vốn", "MASK", 5, true));
            System.out.println(">>> Seeded COMMON_CODE LOCK_MENU");
        }
    }
}
