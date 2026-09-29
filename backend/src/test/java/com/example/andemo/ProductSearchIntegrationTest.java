package com.example.andemo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** GET /api/products?keyword= (bài mẫu migrate Nexacro: màn hình tra cứu sản phẩm). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductSearchIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String userToken;

    @BeforeEach
    void login() {
        ResponseEntity<JsonNode> res = rest.postForEntity("/api/auth/login",
                Map.of("username", "user", "password", "123456"), JsonNode.class);
        userToken = res.getBody().get("token").asText();
    }

    @Test
    void emptyKeywordReturnsAllProducts() {
        JsonNode body = search("/api/products", userToken).getBody();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isGreaterThanOrEqualTo(7);
    }

    @Test
    void keywordMatchesNameIgnoringCase() {
        JsonNode body = search("/api/products?keyword=SỮA", userToken).getBody();
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).get("barcode").asText()).isEqualTo("8851993123456");
    }

    @Test
    void keywordMatchesBarcode() {
        JsonNode body = search("/api/products?keyword=89012345", userToken).getBody();
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).get("name").asText()).contains("Vĩnh Hảo");
    }

    @Test
    void noMatchReturnsEmptyArray() {
        JsonNode body = search("/api/products?keyword=khong-co-san-pham-nay", userToken).getBody();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isZero();
    }

    @Test
    void requiresLogin() {
        assertThat(search("/api/products", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<JsonNode> search(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }
}
