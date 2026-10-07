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
 * Hủy hàng (폐기), cùng định dạng JSON với backend thật (xem class Disposals ở cuối file):
 *   GET  /api/disposals?status=REGISTERED&keyword=sua     danh sách (status: REGISTERED | CONFIRMED | CANCELLED | trống)
 *   GET  /api/disposals/{no}                               chi tiết
 *   PUT  /api/disposals/{no}   {"version","remark","items":[{"itemCode","qty","reasonCode"}]}   lưu
 *   POST /api/disposals/{no}/confirm | cancel | cancel-confirm   {"version","reason"}
 *   POST /api/disposals, GET /api/disposals/reasons, GET /api/inventory/{itemCode}
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
        } else if (path.startsWith("/api/disposals") || path.startsWith("/api/inventory/")) {
            // Hủy hàng (폐기): cùng hợp đồng API với backend thật, dữ liệu giả trong RAM
            Disposals.Result r = Disposals.handle(method, path, query, body, caller);
            send(ex, r.code, r.body);
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
                : query.getOrDefault("message", "PDA \u0111ang \u0111\u01B0\u1EE3c t\u00ECm ki\u1EBFm b\u1EDFi qu\u1EA3n l\u00FD");
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

    // =====================================================================================
    // Hủy hàng (폐기): mock đủ để chạy các màn hủy hàng của app khi không có backend thật.
    // Quy tắc chính giống backend: chỉ phiếu 등록 mới sửa / xác nhận / hủy được, version chống ghi đè (409),
    // xác nhận thì trừ tồn (thiếu tồn → 409), hủy xác nhận thì cộng lại.
    // =====================================================================================
    static class Disposals {

        static class Result {
            final int code;
            final String body;

            Result(int code, String body) {
                this.code = code;
                this.body = body;
            }
        }

        static class Item {
            final String code;
            final String name;
            final long costPrice;
            final long salePrice;
            long onHand;

            Item(String code, String name, long costPrice, long salePrice, long onHand) {
                this.code = code;
                this.name = name;
                this.costPrice = costPrice;
                this.salePrice = salePrice;
                this.onHand = onHand;
            }
        }

        static class Line {
            String itemCode;
            long qty;
            String reasonCode;
        }

        static class Slip {
            String no;
            String businessDate;
            String status = "REGISTERED";
            String remark;
            String registeredBy;
            String registeredAt;
            String updatedBy;
            String updatedAt;
            String confirmedBy;
            String confirmedAt;
            String cancelledBy;
            String cancelledAt;
            String cancelReason;
            long version;
            final List<Line> lines = new ArrayList<>();
        }

        private static final String STORE = "S001";
        private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
        private static final Map<String, Slip> SLIPS = new LinkedHashMap<>();
        private static final Map<String, String[]> REASONS = new LinkedHashMap<>(); // code → {ko, vi}
        private static int seq;
        private static final Pattern ITEM_OBJ = Pattern.compile("\\{[^{}]*\\}");

        static {
            REASONS.put("EXPIRED", new String[]{"\uC720\uD1B5\uAE30\uD55C \uACBD\uACFC", "H\u1EBFt h\u1EA1n s\u1EED d\u1EE5ng"});
            REASONS.put("DAMAGED", new String[]{"\uD30C\uC190", "H\u01B0 h\u1ECFng / v\u1EE1"});
            REASONS.put("SPOILED", new String[]{"\uBCC0\uC9C8", "Bi\u1EBFn ch\u1EA5t"});
            REASONS.put("RECALL", new String[]{"\uD68C\uC218", "Thu h\u1ED3i"});
            REASONS.put("QUALITY", new String[]{"\uD488\uC9C8\uBD88\uB7C9", "L\u1ED7i ch\u1EA5t l\u01B0\u1EE3ng"});
            REASONS.put("OTHER", new String[]{"\uAE30\uD0C0", "Kh\u00E1c"});

            item("8801111222333", "Kimbap tam gi\u00E1c c\u00E1 ng\u1EEB", 800, 1500, 20);
            item("8801234567890", "S\u1EEFa chu\u1ED1i 240ml", 900, 1700, 30);
            item("8809876543210", "B\u00E1nh m\u00EC k\u1EB9p tr\u1EE9ng", 1200, 2500, 5);
            item("8801111000011", "C\u01A1m h\u1ED9p th\u1ECBt b\u00F2", 2500, 4900, 8);
            item("8801111000028", "Sandwich g\u00E0", 1500, 2900, 2);
            item("8801111000035", "S\u1EEFa chua d\u00E2u", 600, 1200, 40);

            String today = java.time.LocalDate.now().toString();
            String yesterday = java.time.LocalDate.now().minusDays(1).toString();
            slip(today, "user", "H\u00E0ng h\u1EBFt h\u1EA1n bu\u1ED5i s\u00E1ng", "REGISTERED",
                    line("8801111222333", 3, "EXPIRED"), line("8801234567890", 2, "EXPIRED"));
            slip(today, "user", "R\u01A1i v\u1EE1 khi tr\u01B0ng b\u00E0y", "REGISTERED",
                    line("8809876543210", 1, "DAMAGED"), line("8801111000035", 4, "DAMAGED"),
                    line("8801111000011", 2, "SPOILED"));
            slip(today, "admin", "Thi\u1EBFu t\u1ED3n (th\u1EED l\u1ED7i x\u00E1c nh\u1EADn)", "REGISTERED",
                    line("8801111000028", 5, "QUALITY"));
            slip(yesterday, "user", "\u0110\u00E3 x\u00E1c nh\u1EADn h\u00F4m qua", "CONFIRMED",
                    line("8801111222333", 2, "EXPIRED"));
            slip(yesterday, "admin", "Nh\u1EADp nh\u1EA7m", "CANCELLED",
                    line("8801234567890", 1, "OTHER"));
        }

        private static void item(String code, String name, long cost, long sale, long onHand) {
            ITEMS.put(code, new Item(code, name, cost, sale, onHand));
        }

        private static Line line(String itemCode, long qty, String reason) {
            Line l = new Line();
            l.itemCode = itemCode;
            l.qty = qty;
            l.reasonCode = reason;
            return l;
        }

        private static Slip slip(String date, String by, String remark, String status, Line... lines) {
            Slip s = new Slip();
            s.no = "D" + date.replace("-", "") + "-" + String.format("%04d", ++seq);
            s.businessDate = date;
            s.registeredBy = by;
            s.registeredAt = Instant.now().toString();
            s.remark = remark;
            s.status = status;
            if ("CONFIRMED".equals(status)) {
                s.confirmedBy = by;
                s.confirmedAt = s.registeredAt;
            } else if ("CANCELLED".equals(status)) {
                s.cancelledBy = by;
                s.cancelledAt = s.registeredAt;
                s.cancelReason = remark;
            }
            s.lines.addAll(List.of(lines));
            SLIPS.put(s.no, s);
            return s;
        }

        static Result handle(String method, String path, Map<String, String> query, String body, String caller) {
            String rest = path.substring("/api".length()); // /disposals..., /inventory/...
            if (method.equals("GET") && rest.startsWith("/inventory/")) {
                Item it = ITEMS.get(rest.substring("/inventory/".length()));
                return it == null ? error(404, "NOT_FOUND", "Kh\u00F4ng c\u00F3 m\u1EB7t h\u00E0ng n\u00E0y")
                        : new Result(200, "{\"itemCode\":" + str(it.code) + ",\"itemName\":" + str(it.name)
                        + ",\"onHandQty\":" + it.onHand + ",\"availableQty\":" + it.onHand
                        + ",\"costPrice\":" + it.costPrice + ",\"salePrice\":" + it.salePrice + "}");
            }
            if (method.equals("GET") && rest.equals("/disposals/reasons")) {
                StringBuilder sb = new StringBuilder("[");
                for (Map.Entry<String, String[]> e : REASONS.entrySet()) {
                    sb.append(sb.length() > 1 ? "," : "").append("{\"code\":").append(str(e.getKey()))
                            .append(",\"koreanName\":").append(str(e.getValue()[0]))
                            .append(",\"vietnameseName\":").append(str(e.getValue()[1])).append('}');
                }
                return new Result(200, sb.append(']').toString());
            }
            if (method.equals("GET") && rest.equals("/disposals")) {
                return new Result(200, list(query));
            }
            if (method.equals("POST") && rest.equals("/disposals")) {
                return register(body, caller);
            }
            String[] parts = rest.substring("/disposals/".length()).split("/");
            Slip s = parts.length > 0 ? SLIPS.get(parts[0]) : null;
            if (s == null) {
                return error(404, "NOT_FOUND", "Kh\u00F4ng c\u00F3 phi\u1EBFu h\u1EE7y n\u00E0y");
            }
            if (parts.length == 1 && method.equals("GET")) {
                return new Result(200, detail(s));
            }
            if (parts.length == 1 && method.equals("PUT")) {
                return update(s, body, caller);
            }
            if (parts.length == 2 && method.equals("POST")) {
                return action(s, parts[1], body, caller);
            }
            return error(404, "NOT_FOUND", "API kh\u00F4ng c\u00F3");
        }

        private static String list(Map<String, String> query) {
            String status = query.getOrDefault("status", "");
            String keyword = query.getOrDefault("keyword", "").trim().toLowerCase();
            String from = query.getOrDefault("from", "");
            String to = query.getOrDefault("to", "");
            List<Slip> result = new ArrayList<>();
            for (Slip s : SLIPS.values()) {
                if (!status.isEmpty() && !status.equals(s.status)) continue;
                if (!from.isEmpty() && s.businessDate.compareTo(from) < 0) continue;
                if (!to.isEmpty() && s.businessDate.compareTo(to) > 0) continue;
                if (!keyword.isEmpty() && !matches(s, keyword)) continue;
                result.add(s);
            }
            result.sort((a, b) -> b.no.compareTo(a.no)); // mới nhất trước
            StringBuilder sb = new StringBuilder("[");
            for (Slip s : result) {
                sb.append(sb.length() > 1 ? "," : "").append(summary(s));
            }
            return sb.append(']').toString();
        }

        /** Từ khóa khớp số phiếu, ghi chú, mã hoặc tên mặt hàng */
        private static boolean matches(Slip s, String keyword) {
            if (s.no.toLowerCase().contains(keyword)
                    || (s.remark != null && s.remark.toLowerCase().contains(keyword))) {
                return true;
            }
            for (Line l : s.lines) {
                Item it = ITEMS.get(l.itemCode);
                if (l.itemCode.contains(keyword) || (it != null && it.name.toLowerCase().contains(keyword))) {
                    return true;
                }
            }
            return false;
        }

        private static String summary(Slip s) {
            long qty = 0, cost = 0, sale = 0;
            for (Line l : s.lines) {
                Item it = ITEMS.get(l.itemCode);
                qty += l.qty;
                cost += it == null ? 0 : it.costPrice * l.qty;
                sale += it == null ? 0 : it.salePrice * l.qty;
            }
            return "{\"disposalNo\":" + str(s.no) + ",\"storeCode\":" + str(STORE)
                    + ",\"businessDate\":" + str(s.businessDate) + statusFields(s)
                    + ",\"remark\":" + str(s.remark) + ",\"registeredBy\":" + str(s.registeredBy)
                    + ",\"registeredAt\":" + str(s.registeredAt) + ",\"lineCount\":" + s.lines.size()
                    + ",\"totalQty\":" + qty + ",\"totalCostAmount\":" + cost + ",\"totalSaleAmount\":" + sale + "}";
        }

        private static String statusFields(Slip s) {
            String code = s.status.equals("REGISTERED") ? "10" : s.status.equals("CONFIRMED") ? "20" : "90";
            String name = s.status.equals("REGISTERED") ? "\uB4F1\uB85D" : s.status.equals("CONFIRMED") ? "\uD655\uC815" : "\uCDE8\uC18C";
            return ",\"status\":" + str(s.status) + ",\"statusCode\":" + str(code) + ",\"statusName\":" + str(name);
        }

        private static String detail(Slip s) {
            boolean registered = s.status.equals("REGISTERED");
            boolean today = s.businessDate.equals(java.time.LocalDate.now().toString());
            List<String> issues = new ArrayList<>();
            StringBuilder items = new StringBuilder("[");
            long qty = 0, cost = 0, sale = 0;
            int no = 0;
            for (Line l : s.lines) {
                Item it = ITEMS.get(l.itemCode);
                long cp = it == null ? 0 : it.costPrice;
                long sp = it == null ? 0 : it.salePrice;
                boolean sufficient = it != null && l.qty <= it.onHand;
                if (registered && !sufficient) {
                    issues.add("Thi\u1EBFu t\u1ED3n: " + (it == null ? l.itemCode : it.name) + " h\u1EE7y " + l.qty
                            + ", kh\u1EA3 d\u1EE5ng " + (it == null ? 0 : it.onHand));
                }
                qty += l.qty;
                cost += cp * l.qty;
                sale += sp * l.qty;
                String[] reason = REASONS.getOrDefault(l.reasonCode, new String[]{l.reasonCode, l.reasonCode});
                items.append(no > 0 ? "," : "").append("{\"lineNo\":").append(++no)
                        .append(",\"itemCode\":").append(str(l.itemCode))
                        .append(",\"itemName\":").append(str(it == null ? null : it.name))
                        .append(",\"qty\":").append(l.qty)
                        .append(",\"reasonCode\":").append(str(l.reasonCode))
                        .append(",\"reasonName\":").append(str(reason[0]))
                        .append(",\"costPrice\":").append(cp).append(",\"salePrice\":").append(sp)
                        .append(",\"costAmount\":").append(cp * l.qty).append(",\"saleAmount\":").append(sp * l.qty)
                        .append(",\"onHandQty\":").append(it == null ? "null" : String.valueOf(it.onHand))
                        .append(",\"availableQty\":").append(it == null ? "null" : String.valueOf(it.onHand))
                        .append(",\"sufficient\":").append(sufficient).append('}');
            }
            if (registered && !today) {
                issues.add("Ch\u1EC9 x\u00E1c nh\u1EADn \u0111\u01B0\u1EE3c phi\u1EBFu c\u1EE7a ng\u00E0y h\u00F4m nay (\uC601\uC5C5\uC77C\uC790 " + s.businessDate + ")");
            }
            StringBuilder iss = new StringBuilder("[");
            for (String i : issues) {
                iss.append(iss.length() > 1 ? "," : "").append(str(i));
            }
            return "{\"disposalNo\":" + str(s.no) + ",\"storeCode\":" + str(STORE)
                    + ",\"businessDate\":" + str(s.businessDate) + statusFields(s)
                    + ",\"remark\":" + str(s.remark)
                    + ",\"registeredBy\":" + str(s.registeredBy) + ",\"registeredAt\":" + str(s.registeredAt)
                    + ",\"updatedBy\":" + str(s.updatedBy) + ",\"updatedAt\":" + str(s.updatedAt)
                    + ",\"confirmedBy\":" + str(s.confirmedBy) + ",\"confirmedAt\":" + str(s.confirmedAt)
                    + ",\"cancelledBy\":" + str(s.cancelledBy) + ",\"cancelledAt\":" + str(s.cancelledAt)
                    + ",\"cancelReason\":" + str(s.cancelReason)
                    + ",\"confirmCancelledBy\":null,\"confirmCancelledAt\":null,\"confirmCancelReason\":null"
                    + ",\"version\":" + s.version + ",\"totalQty\":" + qty
                    + ",\"totalCostAmount\":" + cost + ",\"totalSaleAmount\":" + sale + ",\"closed\":false"
                    + ",\"actions\":{\"edit\":" + registered + ",\"cancel\":" + registered
                    + ",\"confirm\":" + registered + ",\"cancelConfirm\":" + s.status.equals("CONFIRMED") + "}"
                    + ",\"issues\":" + iss.append(']') + ",\"items\":" + items.append(']') + "}";
        }

        /** Đọc mảng "items" của body: [{"itemCode","qty","reasonCode"}, ...]; null nếu sai định dạng */
        private static List<Line> parseLines(String body) {
            int start = body.indexOf("\"items\"");
            if (start < 0) {
                return null;
            }
            List<Line> lines = new ArrayList<>();
            Matcher m = ITEM_OBJ.matcher(body.substring(start));
            while (m.find()) {
                String obj = m.group();
                String code = jsonField(obj, "itemCode");
                String qty = jsonField(obj, "qty");
                if (code == null || qty == null) {
                    return null;
                }
                Line l = new Line();
                l.itemCode = code;
                l.qty = Long.parseLong(qty);
                String reason = jsonField(obj, "reasonCode");
                l.reasonCode = reason == null ? "OTHER" : reason;
                lines.add(l);
            }
            return lines;
        }

        private static Result validateLines(List<Line> lines) {
            if (lines == null || lines.isEmpty()) {
                return error(400, "INVALID", "Phi\u1EBFu ph\u1EA3i c\u00F3 \u00EDt nh\u1EA5t 1 d\u00F2ng h\u00E0ng");
            }
            for (Line l : lines) {
                if (l.qty < 1 || l.qty > 9999) {
                    return error(400, "INVALID", "S\u1ED1 l\u01B0\u1EE3ng ph\u1EA3i t\u1EEB 1 \u0111\u1EBFn 9999 (m\u00E3 " + l.itemCode + ")");
                }
                if (!ITEMS.containsKey(l.itemCode)) {
                    return error(400, "INVALID", "Kh\u00F4ng c\u00F3 m\u1EB7t h\u00E0ng " + l.itemCode);
                }
            }
            return null;
        }

        private static Result register(String body, String caller) {
            List<Line> lines = parseLines(body);
            Result bad = validateLines(lines);
            if (bad != null) {
                return bad;
            }
            Slip s = slip(java.time.LocalDate.now().toString(), caller, jsonField(body, "remark"), "REGISTERED");
            s.lines.addAll(lines);
            return new Result(201, detail(s));
        }

        private static Result update(Slip s, String body, String caller) {
            Result stale = checkVersion(s, body);
            if (stale != null) {
                return stale;
            }
            if (!s.status.equals("REGISTERED")) {
                return error(409, "INVALID_STATUS", "Ch\u1EC9 s\u1EEDa \u0111\u01B0\u1EE3c phi\u1EBFu \u1EDF tr\u1EA1ng th\u00E1i \uB4F1\uB85D");
            }
            List<Line> lines = parseLines(body);
            Result bad = validateLines(lines);
            if (bad != null) {
                return bad;
            }
            s.lines.clear();
            s.lines.addAll(lines);
            String remark = jsonField(body, "remark");
            if (remark != null && remark.length() > 100) {
                return error(400, "INVALID", "Ghi ch\u00FA t\u1ED1i \u0111a 100 k\u00FD t\u1EF1");
            }
            s.remark = remark;
            touch(s, caller);
            return new Result(200, detail(s));
        }

        private static Result action(Slip s, String action, String body, String caller) {
            Result stale = checkVersion(s, body);
            if (stale != null) {
                return stale;
            }
            String reason = jsonField(body, "reason");
            switch (action) {
                case "confirm": {
                    if (!s.status.equals("REGISTERED")) {
                        return error(409, "INVALID_STATUS", "Ch\u1EC9 x\u00E1c nh\u1EADn \u0111\u01B0\u1EE3c phi\u1EBFu \uB4F1\uB85D");
                    }
                    if (!s.businessDate.equals(java.time.LocalDate.now().toString())) {
                        return error(409, "NOT_TODAY", "Ch\u1EC9 x\u00E1c nh\u1EADn \u0111\u01B0\u1EE3c phi\u1EBFu c\u1EE7a ng\u00E0y h\u00F4m nay");
                    }
                    List<String> shortages = new ArrayList<>();
                    for (Line l : s.lines) {
                        Item it = ITEMS.get(l.itemCode);
                        if (l.qty > it.onHand) {
                            shortages.add(it.name + ": h\u1EE7y " + l.qty + ", t\u1ED3n " + it.onHand);
                        }
                    }
                    if (!shortages.isEmpty()) {
                        return error(409, "INSUFFICIENT_STOCK", "Kh\u00F4ng \u0111\u1EE7 t\u1ED3n \u0111\u1EC3 x\u00E1c nh\u1EADn", shortages);
                    }
                    for (Line l : s.lines) {
                        ITEMS.get(l.itemCode).onHand -= l.qty; // 수불: trừ tồn
                    }
                    s.status = "CONFIRMED";
                    s.confirmedBy = caller;
                    s.confirmedAt = Instant.now().toString();
                    break;
                }
                case "cancel":
                    if (!s.status.equals("REGISTERED")) {
                        return error(409, "INVALID_STATUS", "Ch\u1EC9 h\u1EE7y \u0111\u01B0\u1EE3c phi\u1EBFu \uB4F1\uB85D");
                    }
                    if (reason == null || reason.isBlank()) {
                        return error(400, "INVALID", "C\u1EA7n nh\u1EADp l\u00FD do h\u1EE7y");
                    }
                    s.status = "CANCELLED";
                    s.cancelledBy = caller;
                    s.cancelledAt = Instant.now().toString();
                    s.cancelReason = reason;
                    break;
                case "cancel-confirm":
                    if (!s.status.equals("CONFIRMED")) {
                        return error(409, "INVALID_STATUS", "Ch\u1EC9 h\u1EE7y x\u00E1c nh\u1EADn \u0111\u01B0\u1EE3c phi\u1EBFu \uD655\uC815");
                    }
                    if (reason == null || reason.isBlank()) {
                        return error(400, "INVALID", "C\u1EA7n nh\u1EADp l\u00FD do h\u1EE7y x\u00E1c nh\u1EADn");
                    }
                    for (Line l : s.lines) {
                        ITEMS.get(l.itemCode).onHand += l.qty; // 역분개: cộng lại tồn
                    }
                    s.status = "REGISTERED";
                    s.confirmedBy = null;
                    s.confirmedAt = null;
                    break;
                default:
                    return error(404, "NOT_FOUND", "Thao t\u00E1c kh\u00F4ng c\u00F3: " + action);
            }
            touch(s, caller);
            return new Result(200, detail(s));
        }

        /** version trong body phải bằng version hiện tại; khác = người khác vừa sửa (409) */
        private static Result checkVersion(Slip s, String body) {
            String v = jsonField(body, "version");
            if (v == null) {
                return error(400, "INVALID", "Thi\u1EBFu version");
            }
            if (Long.parseLong(v) != s.version) {
                return error(409, "STALE", "Phi\u1EBFu \u0111\u00E3 b\u1ECB ng\u01B0\u1EDDi kh\u00E1c thay \u0111\u1ED5i. H\u00E3y t\u1EA3i l\u1EA1i.");
            }
            return null;
        }

        private static void touch(Slip s, String caller) {
            s.version++;
            s.updatedBy = caller;
            s.updatedAt = Instant.now().toString();
        }

        private static Result error(int code, String errorCode, String message) {
            return error(code, errorCode, message, List.of());
        }

        private static Result error(int code, String errorCode, String message, List<String> details) {
            StringBuilder d = new StringBuilder("[");
            for (String x : details) {
                d.append(d.length() > 1 ? "," : "").append(str(x));
            }
            return new Result(code, "{\"code\":" + str(errorCode) + ",\"message\":" + str(message)
                    + ",\"details\":" + d.append(']') + "}");
        }
    }
}
