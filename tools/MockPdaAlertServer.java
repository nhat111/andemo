import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock server cho PDA Finder polling. Không cần thư viện ngoài, chỉ cần JDK 17+.
 *
 * Chạy:   java tools/MockPdaAlertServer.java            (cổng mặc định 8081)
 *         java tools/MockPdaAlertServer.java 9000       (cổng khác)
 *
 * Tạo lệnh tìm PDA:
 *   curl -X POST "http://localhost:8081/api/pda/alerts?message=Tim%20may%20kho%20A"
 *   curl -X POST "http://localhost:8081/api/pda/alerts?deviceId=<ANDROID_ID>&ttl=300"
 * Xem trạng thái các lệnh:
 *   curl http://localhost:8081/api/pda/alerts
 *
 * Có thêm /api/auth/login và /api/items giả để app login được.
 * Dữ liệu chỉ nằm trong RAM, tắt server là mất.
 */
public class MockPdaAlertServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final Pattern USERNAME = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]*)\"");
    // Thứ tự trạng thái: không cho trạng thái lùi (ví dụ DELIVERED gửi lại sau STOPPED_BY_USER)
    private static final List<String> STATUS_ORDER =
            List.of("SENT", "DELIVERED", "STOPPED_BY_USER", "TIMED_OUT");

    private static final Map<String, Alert> alerts = new LinkedHashMap<>();
    private static final AtomicInteger counter = new AtomicInteger();

    static class Alert {
        String requestId;
        String deviceId; // null = mọi thiết bị
        String message;
        long expiresAtMs;
        String status = "SENT";
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8081;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", MockPdaAlertServer::handle);
        server.start();
        System.out.println("Mock PDA alert server on http://localhost:" + port);
        System.out.println("Emulator: -PapiBaseUrl=http://10.0.2.2:" + port + "/ | "
                + "Real device: -PapiBaseUrl=http://<LAN IP of this PC>:" + port + "/");
    }

    private static synchronized void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        log(method + " " + ex.getRequestURI() + (body.isEmpty() ? "" : " " + body));

        if (method.equals("POST") && path.equals("/api/auth/login")) {
            Matcher m = USERNAME.matcher(body);
            String username = m.find() ? m.group(1) : "user";
            send(ex, 200, "{\"token\":\"mock-token\",\"role\":\"USER\",\"username\":\"" + json(username) + "\"}");
        } else if (method.equals("GET") && path.equals("/api/items")) {
            send(ex, 200, "[]");
        } else if (method.equals("GET") && path.equals("/api/pda/alerts/pending")) {
            send(ex, 200, pending(query.get("deviceId")));
        } else if (method.equals("POST") && path.equals("/api/pda/alerts")) {
            send(ex, 201, create(query));
        } else if (method.equals("GET") && path.equals("/api/pda/alerts")) {
            send(ex, 200, listAll());
        } else if (method.equals("POST") && path.startsWith("/api/pda/alerts/") && path.endsWith("/ack")) {
            String requestId = path.substring("/api/pda/alerts/".length(), path.length() - "/ack".length());
            send(ex, ack(requestId, body) ? 204 : 404, null);
        } else {
            send(ex, 404, "{\"error\":\"not found\"}");
        }
    }

    private static String pending(String deviceId) {
        long now = System.currentTimeMillis();
        List<String> items = new ArrayList<>();
        for (Alert a : alerts.values()) {
            boolean forDevice = a.deviceId == null || a.deviceId.equals(deviceId);
            if (forDevice && a.status.equals("SENT") && a.expiresAtMs > now) {
                items.add("{\"requestId\":\"" + a.requestId + "\",\"message\":\"" + json(a.message)
                        + "\",\"storeCode\":\"STORE01\"}");
            }
        }
        return "[" + String.join(",", items) + "]";
    }

    private static String create(Map<String, String> query) {
        Alert a = new Alert();
        a.requestId = "REQ-" + LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
                + "-" + counter.incrementAndGet();
        a.deviceId = query.get("deviceId");
        a.message = query.getOrDefault("message", "PDA đang được tìm kiếm bởi quản lý");
        long ttlSeconds = Long.parseLong(query.getOrDefault("ttl", "120"));
        a.expiresAtMs = System.currentTimeMillis() + ttlSeconds * 1000;
        alerts.put(a.requestId, a);
        log(">>> Created " + a.requestId + " for " + (a.deviceId == null ? "any device" : a.deviceId)
                + ", expires in " + ttlSeconds + "s");
        return toJson(a);
    }

    private static boolean ack(String requestId, String body) {
        Alert a = alerts.get(requestId);
        if (a == null) {
            return false;
        }
        Matcher m = STATUS.matcher(body);
        if (m.find()) {
            String status = m.group(1);
            if (STATUS_ORDER.indexOf(status) > STATUS_ORDER.indexOf(a.status)) {
                a.status = status;
                log(">>> " + requestId + " is now " + status);
            }
        }
        return true;
    }

    private static String listAll() {
        List<String> items = new ArrayList<>();
        for (Alert a : alerts.values()) {
            items.add(toJson(a));
        }
        return "[" + String.join(",", items) + "]";
    }

    private static String toJson(Alert a) {
        return "{\"requestId\":\"" + a.requestId + "\",\"deviceId\":"
                + (a.deviceId == null ? "null" : "\"" + json(a.deviceId) + "\"")
                + ",\"message\":\"" + json(a.message) + "\",\"status\":\"" + a.status
                + "\",\"expiresAt\":\"" + Instant.ofEpochMilli(a.expiresAtMs) + "\"}";
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                result.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        if (body == null) {
            ex.sendResponseHeaders(code, -1);
            ex.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void log(String line) {
        System.out.println(LocalTime.now().format(TIME) + " " + line);
    }
}
