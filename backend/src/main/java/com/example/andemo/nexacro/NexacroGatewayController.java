package com.example.andemo.nexacro;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cổng chuyển đổi cho app Android: nhận JSON → đổi sang XML Dataset → gọi server Nexacro cũ (*.do)
 * → đổi kết quả XML → JSON. Không phải sửa gì ở server cũ.
 *
 * POST /api/nx/product/search  ⇒  POST {legacy-base-url}product/search.do
 *
 * Cần đăng nhập (JWT) như mọi API khác của app. ErrorCode &lt; 0 từ server cũ → HTTP 400,
 * không gọi được server cũ → HTTP 502.
 */
@Slf4j
@RestController
@RequestMapping("/api/nx")
public class NexacroGatewayController {

    /** Chỉ cho phép đường dẫn dạng a/b_c: không có "..", query, hay host khác */
    private static final Pattern SERVICE_PATH = Pattern.compile("^[A-Za-z0-9_]+(/[A-Za-z0-9_]+)*$");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Environment env;
    private final String legacyBaseUrl;

    public NexacroGatewayController(Environment env,
                                    @Value("${app.nexacro.legacy-base-url:}") String legacyBaseUrl) {
        this.env = env;
        this.legacyBaseUrl = legacyBaseUrl;
    }

    @PostMapping("/**")
    public ResponseEntity<Map<String, Object>> call(HttpServletRequest request,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        String service = request.getRequestURI().substring((request.getContextPath() + "/api/nx/").length());
        if (!SERVICE_PATH.matcher(service).matches()) {
            return ResponseEntity.badRequest().body(error(-900, "Tên service không hợp lệ: " + service));
        }

        String xmlRequest = NexacroXml.write(NexacroJson.fromJson(body == null ? Map.of() : body));
        URI target = URI.create(baseUrl() + service + ".do");
        String xmlResponse;
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(target)
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "text/xml; charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(xmlRequest, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                log.warn("Nexacro service {} returned HTTP {}", service, res.statusCode());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(error(-901, "Server Nexacro trả HTTP " + res.statusCode()));
            }
            xmlResponse = res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(-902, "Bị ngắt khi gọi server Nexacro"));
        } catch (Exception e) {
            log.warn("Cannot call Nexacro service {}: {}", service, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(error(-902, "Không gọi được server Nexacro"));
        }

        Map<String, Object> json = NexacroJson.toJson(NexacroXml.parse(xmlResponse));
        int errorCode = (Integer) json.get("errorCode");
        return errorCode < 0 ? ResponseEntity.badRequest().body(json) : ResponseEntity.ok(json);
    }

    /** Mặc định gọi server Nexacro giả lập ngay trong backend này (LegacyNexacroController). */
    private String baseUrl() {
        if (legacyBaseUrl != null && !legacyBaseUrl.isBlank()) {
            return legacyBaseUrl.endsWith("/") ? legacyBaseUrl : legacyBaseUrl + "/";
        }
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        return "http://localhost:" + port + "/nexacro/";
    }

    private static Map<String, Object> error(int code, String message) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("errorCode", code);
        json.put("errorMsg", message);
        json.put("params", Map.of());
        json.put("datasets", Map.of());
        return json;
    }
}
