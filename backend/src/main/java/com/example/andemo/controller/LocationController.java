package com.example.andemo.controller;

import com.example.andemo.dto.LocationRequest;
import com.example.andemo.dto.NearbyUserDto;
import com.example.andemo.entity.User;
import com.example.andemo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LocationController {

    private final UserRepository userRepository;

    /** Cập nhật vị trí của user đang login */
    @PostMapping("/update")
    public ResponseEntity<?> updateLocation(
            @RequestBody LocationRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setLatitude(request.getLatitude());
        user.setLongitude(request.getLongitude());
        userRepository.save(user);

        return ResponseEntity.ok("Đã cập nhật vị trí");
    }

    /**
     * Lấy danh sách user gần đây
     * @param radiusKm bán kính (mặc định 10km)
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<NearbyUserDto>> getNearby(
            @RequestParam(defaultValue = "10") double radiusKm,
            Authentication authentication) {

        String myUsername = authentication.getName();
        User me = userRepository.findByUsername(myUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (me.getLatitude() == null || me.getLongitude() == null) {
            return ResponseEntity.badRequest().build(); // chưa có location
        }

        List<NearbyUserDto> nearby = userRepository.findAll().stream()
                .filter(u -> !u.getUsername().equals(myUsername)) // bỏ chính mình
                .filter(u -> u.getLatitude() != null && u.getLongitude() != null)
                .map(u -> {
                    double dist = haversine(
                            me.getLatitude(), me.getLongitude(),
                            u.getLatitude(), u.getLongitude()
                    );
                    return new NearbyUserDto(
                            u.getUsername(),
                            u.getRole(),
                            Math.round(dist * 100.0) / 100.0, // làm tròn 2 số
                            u.getLatitude(),
                            u.getLongitude()
                    );
                })
                .filter(dto -> dto.getDistanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(NearbyUserDto::getDistanceKm))
                .collect(Collectors.toList());

        return ResponseEntity.ok(nearby);
    }

    /** Công thức Haversine tính khoảng cách 2 điểm GPS (km) */
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // bán kính Trái Đất (km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
