package com.example.andemo;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthRefreshIntegrationTest {

    private static final String PROTECTED_URL = "/api/pda/alerts/pending?deviceId=auth-test";

    @Autowired
    private TestRestTemplate rest;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void loginReturnsAccessAndRefreshToken() {
        Map<?, ?> login = login("user");
        assertThat((String) login.get("token")).isNotBlank();
        assertThat((String) login.get("refreshToken")).isNotBlank();
        assertThat(login.get("role")).isEqualTo("USER");
    }

    @Test
    void wrongPasswordIsUnauthorized() {
        ResponseEntity<String> response = rest.postForEntity("/api/auth/login",
                Map.of("username", "user", "password", "wrong"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshRotatesTokensAndNewAccessTokenWorks() {
        String refreshToken = (String) login("user").get("refreshToken");

        ResponseEntity<Map> refreshed = refresh(refreshToken);

        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newAccess = (String) refreshed.getBody().get("token");
        String newRefresh = (String) refreshed.getBody().get("refreshToken");
        assertThat(newRefresh).isNotEqualTo(refreshToken);
        assertThat(get(PROTECTED_URL, newAccess)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void reusedRefreshTokenRevokesTheWholeFamily() {
        String first = (String) login("user2").get("refreshToken");
        String second = (String) refresh(first).getBody().get("refreshToken");

        // Token đã dùng để xoay vòng bị dùng lại: có thể đã bị lộ
        assertThat(refresh(first).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Toàn bộ token của user bị thu hồi, kể cả token mới nhất
        assertThat(refresh(second).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesRefreshToken() {
        String refreshToken = (String) login("user").get("refreshToken");

        ResponseEntity<Void> logout = rest.postForEntity("/api/auth/logout",
                Map.of("refreshToken", refreshToken), Void.class);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(refresh(refreshToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownRefreshTokenIsUnauthorized() {
        assertThat(refresh("not-a-real-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void missingTokenIsUnauthorized() {
        assertThat(get(PROTECTED_URL, null)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedAccessTokenIsUnauthorizedNotServerError() {
        assertThat(get(PROTECTED_URL, "x.y.z")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(PROTECTED_URL, "garbage")).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredAccessTokenIsUnauthorized() {
        String expired = Jwts.builder()
                .setSubject("user")
                .claim("role", "USER")
                .setIssuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .setExpiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        assertThat(get(PROTECTED_URL, expired)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenSignedWithAnotherKeyIsUnauthorized() {
        String forged = Jwts.builder()
                .setSubject("admin")
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-32chars!!".getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
        assertThat(get(PROTECTED_URL, forged)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----- helpers -----

    private Map<?, ?> login(String username) {
        ResponseEntity<Map> response = rest.postForEntity("/api/auth/login",
                Map.of("username", username, "password", "123456"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map> refresh(String refreshToken) {
        return rest.postForEntity("/api/auth/refresh", Map.of("refreshToken", refreshToken), Map.class);
    }

    private HttpStatusCode get(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode();
    }
}
