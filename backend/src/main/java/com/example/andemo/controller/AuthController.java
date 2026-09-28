package com.example.andemo.controller;

import com.example.andemo.dto.LoginRequest;
import com.example.andemo.dto.LoginResponse;
import com.example.andemo.dto.RefreshTokenRequest;
import com.example.andemo.entity.User;
import com.example.andemo.repository.UserRepository;
import com.example.andemo.service.JwtService;
import com.example.andemo.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(buildResponse(request.getUsername(),
                refreshTokenService.issue(request.getUsername())));
    }

    /**
     * Đổi refresh token lấy access token mới + refresh token mới (token cũ bị thu hồi).
     * 401 = refresh token không hợp lệ / hết hạn / đã bị thu hồi → client phải login lại.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.getRefreshToken());
            return ResponseEntity.ok(buildResponse(rotation.username(), rotation.refreshToken()));
        } catch (RefreshTokenService.InvalidRefreshTokenException | UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /** Thu hồi refresh token. Access token hiện tại vẫn dùng được tới khi hết hạn (ngắn). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    private LoginResponse buildResponse(String username, String refreshToken) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String token = jwtService.generateToken(userDetails);
        User user = userRepository.findByUsername(username).orElseThrow();
        return new LoginResponse(token, user.getRole(), user.getUsername(), refreshToken);
    }
}
