package com.example.andemo;

import com.example.andemo.nexacro.NexacroData;
import com.example.andemo.nexacro.NexacroXml;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demo chuyển đổi X-API (XML Dataset) → JSON: server Nexacro giả lập (/nexacro/*.do)
 * và cổng JSON cho app (/api/nx/**). Xem docs/NEXACRO_XAPI_TO_JSON.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NexacroGatewayIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String userToken;

    @BeforeEach
    void login() {
        ResponseEntity<JsonNode> res = rest.postForEntity("/api/auth/login",
                Map.of("username", "user", "password", "123456"), JsonNode.class);
        userToken = res.getBody().get("token").asText();
    }

    // ---------- Server Nexacro giả lập: XML vào, XML ra (như transaction() gọi thẳng) ----------

    @Test
    void legacyEndpointSpeaksNexacroXml() {
        String request = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                  <Dataset id="ds_search">
                    <ColumnInfo><Column id="keyword" type="string" size="100"/></ColumnInfo>
                    <Rows><Row><Col id="keyword">sữa</Col></Row></Rows>
                  </Dataset>
                </Root>""";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        ResponseEntity<String> res = rest.exchange("/nexacro/product/search.do", HttpMethod.POST,
                new HttpEntity<>(request, headers), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        NexacroData out = NexacroXml.parse(res.getBody());
        assertThat(out.getParams().get("ErrorCode")).isEqualTo(0L);
        assertThat(out.dataset("ds_list").getRows()).hasSize(1);
        assertThat(out.dataset("ds_list").getString(0, "barcode")).isEqualTo("8851993123456");
        assertThat(out.dataset("ds_list").getColumns().get("stockQuantity")).isEqualTo("int");
    }

    // ---------- Cổng JSON: 3 ví dụ ----------

    @Test
    void demo1_searchReturnsDatasetAsJsonArray() {
        ResponseEntity<JsonNode> res = nx("product/search",
                Map.of("datasets", Map.of("ds_search", List.of(Map.of("keyword", "sữa")))), userToken);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = res.getBody();
        assertThat(body.get("errorCode").asInt()).isZero();
        JsonNode list = body.get("datasets").get("ds_list");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("name").asText()).contains("TH True Milk");
        assertThat(list.get(0).get("stockQuantity").isNumber()).as("cột int → số JSON").isTrue();
    }

    @Test
    void demo1_emptyRequestReturnsAllProducts() {
        JsonNode body = nx("product/search", Map.of(), userToken).getBody();
        assertThat(body.get("datasets").get("ds_list").size()).isGreaterThanOrEqualTo(7);
    }

    @Test
    void demo2_multipleDatasetsAndParams() {
        JsonNode body = nx("code/list", Map.of(), userToken).getBody();

        assertThat(body.get("datasets").get("ds_category")).hasSize(3);
        assertThat(body.get("datasets").get("ds_unit")).hasSize(3);
        assertThat(body.get("datasets").get("ds_category").get(0).get("code").asText()).isEqualTo("DRINK");
        assertThat(body.get("params").get("codeVersion").asText()).isEqualTo("20260929");
        assertThat(body.get("params").has("ErrorCode")).as("ErrorCode tách ra errorCode").isFalse();
    }

    @Test
    void demo3_saveWithRowTypes() {
        // Cà phê G7 (8936036020151) tồn 25: nhập 10 (insert), sửa 5 → 8 (update: +3), xoá dòng 2 (delete: −2)
        Map<String, Object> body = Map.of("datasets", Map.of("ds_stock", List.of(
                Map.of("_rowType", "insert", "barcode", "8936036020151", "qty", 10, "note", "Nhập lô A"),
                Map.of("_rowType", "update", "barcode", "8936036020151", "qty", 8,
                        "_orgRow", Map.of("barcode", "8936036020151", "qty", 5)),
                Map.of("_rowType", "delete", "barcode", "8936036020151", "qty", 2,
                        "_orgRow", Map.of("barcode", "8936036020151", "qty", 2)))));

        ResponseEntity<JsonNode> res = nx("stock/save", body, userToken);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("params").get("savedCount").asInt()).isEqualTo(3);
        JsonNode result = res.getBody().get("datasets").get("ds_result");
        assertThat(result.get(0).get("stockQuantity").asInt()).isEqualTo(25 + 10 + 3 - 2);
    }

    @Test
    void demo3_businessErrorBecomesHttp400WithMessage() {
        Map<String, Object> body = Map.of("datasets", Map.of("ds_stock", List.of(
                Map.of("_rowType", "insert", "barcode", "8936036020151", "qty", 0))));

        ResponseEntity<JsonNode> res = nx("stock/save", body, userToken);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("errorCode").asInt()).isEqualTo(-2);
        assertThat(res.getBody().get("errorMsg").asText()).contains("số lượng phải > 0");
    }

    @Test
    void gatewayRequiresLogin() {
        assertThat(nx("product/search", Map.of(), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void gatewayRejectsInvalidServicePath() {
        ResponseEntity<JsonNode> res = nx("product/search.do", Map.of(), userToken);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("errorCode").asInt()).isEqualTo(-900);
    }

    @Test
    void unknownLegacyServiceIsBadGateway() {
        assertThat(nx("khong/ton_tai", Map.of(), userToken).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    private ResponseEntity<JsonNode> nx(String service, Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange("/api/nx/" + service, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
