import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Chạy (từ thư mục gốc repo):
 *   java tools/MockPdaAlertServer.java            (cổng mặc định 8081)
 *   java tools/MockPdaAlertServer.java 9000       (cổng khác)
 *
 * Web quản lý: http://localhost:8081/  (login bằng "admin", mật khẩu bất kỳ)
 *
 * Tạo lệnh tìm PDA bằng curl:
 *   curl -X POST "http://localhost:8081/api/pda/alerts?message=Tim%20may%20kho%20A"
 *   curl -X POST "http://localhost:8081/api/pda/alerts?deviceId=<ANDROID_ID>&ttl=300"
 * Xem PDA đã liên lạc và lịch sử lệnh:
 *   curl http://localhost:8081/api/pda/devices
 *   curl http://localhost:8081/api/pda/alerts
 *
 * Login: "admin" (mật khẩu bất kỳ) được role ADMIN, tên khác được role USER. Giống backend thật,
 * token USER không được tạo lệnh, xem danh sách PDA hay lịch sử (403). curl không gửi token thì
 * được coi như ADMIN cho tiện. Dữ liệu chỉ nằm trong RAM, tắt server là mất.
 */
public class MockPdaAlertServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern USERNAME = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]*)\"");
    // Token mock có dạng "<role>.<username>", ví dụ "ADMIN.admin", "USER.nhanvien1"
    private static final String ADMIN_PREFIX = "ADMIN.";
    private static final String USER_PREFIX = "USER.";
    // PDA được coi là online nếu poll trong khoảng này (3 chu kỳ poll 30 giây)
    private static final long ONLINE_WINDOW_MS = 90_000;
    // Thứ tự trạng thái: không cho trạng thái lùi (ví dụ DELIVERED gửi lại sau STOPPED_BY_USER)
    private static final List<String> STATUS_ORDER =
            List.of("SENT", "DELIVERED", "STOPPED_BY_USER", "TIMED_OUT");
    private static final Path WEB_PAGE = Path.of("backend/src/main/resources/static/pda-finder.html");

    private static final Map<String, Alert> alerts = new LinkedHashMap<>();
    private static final Map<String, Device> devices = new LinkedHashMap<>();
    private static final AtomicInteger counter = new AtomicInteger();

    static class Device {
        String deviceId;
        String deviceName;
        String username; // user đang login trên PDA (theo token của lần poll cuối)
        long lastSeenMs;
    }

    static class Alert {
        String requestId;
        String deviceId; // null = mọi thiết bị
        String storeCode;
        String message;
        String requestedBy;
        long createdAtMs;
        long expiresAtMs;
        String status = "SENT";
        long deliveredAtMs;
        long finishedAtMs;
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8081;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", MockPdaAlertServer::handle);
        server.start();
        System.out.println("Mock PDA alert server on http://localhost:" + port);
        System.out.println("Web: http://localhost:" + port + "/  (login: admin / any password)");
        System.out.println("Emulator: -PapiBaseUrl=http://10.0.2.2:" + port + "/ | "
                + "Real device: -PapiBaseUrl=http://<LAN IP of this PC>:" + port + "/");
        if (!Files.exists(WEB_PAGE)) {
            System.out.println("WARN: " + WEB_PAGE + " not found. Run from the repo root to serve the web page.");
        }
    }

    private static synchronized void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!path.equals("/") && !path.endsWith(".html")) {
            log(method + " " + ex.getRequestURI() + (body.isEmpty() ? "" : " " + body));
        }

        String token = bearerToken(ex);
        String caller = token == null ? "curl" : token.substring(token.indexOf('.') + 1);
        boolean admin = token == null || token.startsWith(ADMIN_PREFIX);
        String adminOnly = "{\"error\":\"ADMIN only\"}";

        if (method.equals("GET") && (path.equals("/") || path.equals("/pda-finder.html"))) {
            sendWebPage(ex);
        } else if (method.equals("POST") && path.equals("/api/auth/login")) {
            Matcher m = USERNAME.matcher(body);
            String username = m.find() ? m.group(1) : "user";
            boolean isAdmin = username.equals("admin");
            send(ex, 200, "{\"token\":\"" + (isAdmin ? ADMIN_PREFIX : USER_PREFIX) + json(username)
                    + "\",\"role\":\"" + (isAdmin ? "ADMIN" : "USER") + "\",\"username\":\"" + json(username) + "\"}");
        } else if (method.equals("GET") && path.equals("/api/items")) {
            send(ex, 200, "[]");
        } else if (method.equals("GET") && path.equals("/api/pda/alerts/pending")) {
            recordDevice(query.get("deviceId"), query.get("deviceName"), token == null ? null : caller);
            send(ex, 200, pending(query.get("deviceId")));
        } else if (method.equals("GET") && path.equals("/api/pda/devices")) {
            send(ex, admin ? 200 : 403, admin ? listDevices() : adminOnly);
        } else if (method.equals("POST") && path.equals("/api/pda/alerts")) {
            send(ex, admin ? 201 : 403, admin ? create(query, body, caller) : adminOnly);
        } else if (method.equals("GET") && path.equals("/api/pda/alerts")) {
            send(ex, admin ? 200 : 403, admin ? listAll() : adminOnly);
        } else if (method.equals("POST") && path.startsWith("/api/pda/alerts/") && path.endsWith("/ack")) {
            String requestId = path.substring("/api/pda/alerts/".length(), path.length() - "/ack".length());
            send(ex, ack(requestId, body) ? 204 : 404, null);
        } else if (method.equals("GET") && path.startsWith("/api/pda/alerts/")) {
            Alert a = alerts.get(path.substring("/api/pda/alerts/".length()));
            if (!admin) {
                send(ex, 403, adminOnly);
            } else {
                send(ex, a == null ? 404 : 200, a == null ? "{\"error\":\"not found\"}" : toJson(a));
            }
        } else {
            send(ex, 404, "{\"error\":\"not found\"}");
        }
    }

    // ----- PDA -----

    private static void recordDevice(String deviceId, String deviceName, String username) {
        if (deviceId == null || deviceId.isEmpty()) {
            return;
        }
        Device d = devices.computeIfAbsent(deviceId, id -> new Device());
        if (d.deviceId == null) {
            log(">>> New PDA: " + deviceId + (deviceName == null ? "" : " (" + deviceName + ")"));
        }
        d.deviceId = deviceId;
        if (deviceName != null) {
            d.deviceName = deviceName;
        }
        if (username != null) {
            d.username = username;
        }
        d.lastSeenMs = System.currentTimeMillis();
    }

    private static String pending(String deviceId) {
        long now = System.currentTimeMillis();
        List<String> items = new ArrayList<>();
        for (Alert a : alerts.values()) {
            boolean forDevice = a.deviceId == null || a.deviceId.equals(deviceId);
            if (forDevice && a.status.equals("SENT") && a.expiresAtMs > now) {
                items.add("{\"requestId\":\"" + a.requestId + "\",\"message\":\"" + json(a.message)
                        + "\",\"storeCode\":" + str(a.storeCode) + "}");
            }
        }
        return "[" + String.join(",", items) + "]";
    }

    private static boolean ack(String requestId, String body) {
        Alert a = alerts.get(requestId);
        if (a == null) {
            return false;
        }
        String status = jsonField(body, "status");
        if (status != null && STATUS_ORDER.indexOf(status) > STATUS_ORDER.indexOf(a.status)
                && !a.status.equals("STOPPED_BY_USER") && !a.status.equals("TIMED_OUT")) {
            long now = System.currentTimeMillis();
            if (a.deliveredAtMs == 0) {
                a.deliveredAtMs = now;
            }
            if (!status.equals("DELIVERED")) {
                a.finishedAtMs = now;
            }
            a.status = status;
            log(">>> " + requestId + " is now " + status);
        }
        return true;
    }

    // ----- Requester (ADMIN) -----

    private static String listDevices() {
        long now = System.currentTimeMillis();
        List<Device> sorted = new ArrayList<>(devices.values());
        sorted.sort((x, y) -> Long.compare(y.lastSeenMs, x.lastSeenMs));
        List<String> items = new ArrayList<>();
        for (Device d : sorted) {
            long ago = now - d.lastSeenMs;
            items.add("{\"deviceId\":\"" + json(d.deviceId) + "\",\"deviceName\":" + str(d.deviceName)
                    + ",\"username\":" + str(d.username)
                    + ",\"lastSeenAt\":\"" + Instant.ofEpochMilli(d.lastSeenMs) + "\""
                    + ",\"secondsSinceLastSeen\":" + (ago / 1000)
                    + ",\"online\":" + (ago < ONLINE_WINDOW_MS) + "}");
        }
        return "[" + String.join(",", items) + "]";
    }

    /** Nhận body JSON như backend thật ({"deviceId","message","storeCode","ttlSeconds"}), hoặc query param cho curl. */
    private static String create(Map<String, String> query, String body, String requestedBy) {
        Alert a = new Alert();
        a.requestId = "REQ-" + LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
                + "-" + counter.incrementAndGet();
        String bodyDeviceId = jsonField(body, "deviceId");
        String bodyMessage = jsonField(body, "message");
        String bodyTtl = jsonField(body, "ttlSeconds");
        a.deviceId = bodyDeviceId != null ? bodyDeviceId : query.get("deviceId");
        a.storeCode = jsonField(body, "storeCode") != null ? jsonField(body, "storeCode") : query.get("storeCode");
        a.message = bodyMessage != null && !bodyMessage.isBlank() ? bodyMessage
                : query.getOrDefault("message", "PDA đang được tìm kiếm bởi quản lý");
        a.requestedBy = requestedBy;
        long ttlSeconds = Long.parseLong(bodyTtl != null ? bodyTtl : query.getOrDefault("ttl", "120"));
        a.createdAtMs = System.currentTimeMillis();
        a.expiresAtMs = a.createdAtMs + ttlSeconds * 1000;
        alerts.put(a.requestId, a);
        log(">>> Created " + a.requestId + " by " + requestedBy + " for "
                + (a.deviceId == null ? "any device" : a.deviceId) + ", expires in " + ttlSeconds + "s");
        return toJson(a);
    }

    /** Lịch sử, lệnh mới nhất đứng đầu. */
    private static String listAll() {
        List<Alert> sorted = new ArrayList<>(alerts.values());
        sorted.sort((x, y) -> Long.compare(y.createdAtMs, x.createdAtMs));
        List<String> items = new ArrayList<>();
        for (Alert a : sorted) {
            items.add(toJson(a));
        }
        return "[" + String.join(",", items) + "]";
    }

    private static String toJson(Alert a) {
        return "{\"requestId\":\"" + a.requestId + "\",\"deviceId\":" + str(a.deviceId)
                + ",\"storeCode\":" + str(a.storeCode)
                + ",\"message\":\"" + json(a.message) + "\",\"requestedBy\":" + str(a.requestedBy)
                + ",\"status\":\"" + a.status + "\""
                + ",\"createdAt\":" + time(a.createdAtMs)
                + ",\"expiresAt\":" + time(a.expiresAtMs)
                + ",\"deliveredAt\":" + time(a.deliveredAtMs)
                + ",\"finishedAt\":" + time(a.finishedAtMs)
                + ",\"expired\":" + (a.expiresAtMs <= System.currentTimeMillis()) + "}";
    }

    // ----- Helpers -----

    private static void sendWebPage(HttpExchange ex) throws IOException {
        if (!Files.exists(WEB_PAGE)) {
            send(ex, 404, "{\"error\":\"Web page not found. Run the mock server from the repo root.\"}");
            return;
        }
        byte[] bytes = Files.readAllBytes(WEB_PAGE);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String time(long epochMs) {
        return epochMs == 0 ? "null" : "\"" + Instant.ofEpochMilli(epochMs) + "\"";
    }

    private static String str(String value) {
        return value == null ? "null" : "\"" + json(value) + "\"";
    }

    /** Lấy giá trị 1 field JSON dạng chuỗi hoặc số (đủ cho mock, không phải parser đầy đủ). */
    private static String jsonField(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|-?\\d+)").matcher(body);
        if (!m.find()) {
            return null;
        }
        return m.group(2) != null ? m.group(2).replace("\\\"", "\"").replace("\\\\", "\\") : m.group(1);
    }

    private static String bearerToken(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        return header != null && header.startsWith("Bearer ") && header.length() > 7 ? header.substring(7) : null;
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
