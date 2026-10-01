package com.example.andemo.plain;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.andemo.BuildConfig;
import com.example.andemo.api.TokenRefresher;
import com.example.andemo.util.PreferenceManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gọi API kiểu "Java thuần" (không Retrofit / Gson): thread pool + HttpURLConnection + org.json.
 * Không cần thư viện ngoài nào: hợp với môi trường không tải được thư viện.
 *
 * Mọi request chạy ở luồng nền; kết quả LUÔN trả về luồng giao diện (callback được phép đụng view).
 *
 *   HttpTask.get(this, "api/disposals?status=REGISTERED", new HttpTask.Callback() { … });
 *   HttpTask.post(this, "api/disposals/" + no + "/confirm", body, callback);
 *
 * Bản tương đương dùng Retrofit: api/ApiClient + api/DisposalApi. So 2 bản: docs/nexacro-migration/PLAIN_JAVA_STYLE.md
 */
public final class HttpTask {

    private static final String TAG = "HttpTask";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    /** 3 luồng nền dùng chung cho cả app (không tạo new Thread mỗi lần gọi) */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Kết quả trả về trên luồng giao diện */
    public interface Callback {
        /** HTTP 2xx: body là chuỗi JSON (có thể rỗng) */
        void onSuccess(String body);

        /**
         * Lỗi: httpCode = mã HTTP (400, 403, 409…), hoặc 0 nếu không tới được server (mất mạng, timeout).
         * message = thông báo server gửi (trường "message" của JSON lỗi) hoặc thông báo chung.
         */
        void onError(int httpCode, String message);
    }

    private HttpTask() {
    }

    public static void get(Context context, String path, Callback callback) {
        request(context, "GET", path, null, callback);
    }

    public static void post(Context context, String path, JSONObject body, Callback callback) {
        request(context, "POST", path, body, callback);
    }

    public static void put(Context context, String path, JSONObject body, Callback callback) {
        request(context, "PUT", path, body, callback);
    }

    private static void request(Context context, String method, String path, JSONObject body, Callback callback) {
        Context app = context.getApplicationContext(); // không giữ Activity trong luồng nền
        EXECUTOR.execute(() -> {
            PreferenceManager pref = new PreferenceManager(app);
            String token = pref.getToken();
            Result r = call(method, path, body, token);
            if (r.code == 401 && token != null) {
                // Access token hết hạn: làm mới 1 lần rồi gọi lại (dùng chung TokenRefresher của app)
                String newToken = TokenRefresher.refresh(app, token);
                if (newToken != null) {
                    r = call(method, path, body, newToken);
                }
            }
            final Result result = r;
            MAIN.post(() -> {
                if (result.code >= 200 && result.code < 300) {
                    callback.onSuccess(result.body);
                } else {
                    callback.onError(result.code, result.message);
                }
            });
        });
    }

    /** Chạy trên luồng nền: KHÔNG được đụng view ở đây */
    private static Result call(String method, String path, JSONObject body, String token) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BuildConfig.API_BASE_URL + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            // Lỗi 4xx / 5xx: nội dung nằm ở getErrorStream(), getInputStream() sẽ ném IOException
            InputStream in = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(in);
            if (BuildConfig.DEBUG) {
                Log.d(TAG, method + " " + path + " → " + code + " " + text);
            }
            return new Result(code, text, code < 400 ? null : errorMessage(code, text));
        } catch (IOException e) {
            Log.w(TAG, method + " " + path + " failed: " + e.getMessage());
            return new Result(0, null, "Không kết nối được server");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream is = in; ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            return buf.toString("UTF-8");
        }
    }

    /** Server trả lỗi dạng {"code": "...", "message": "...", "details": [...]}: ghép message + details */
    private static String errorMessage(int code, String body) {
        try {
            JSONObject json = new JSONObject(body);
            StringBuilder sb = new StringBuilder(json.optString("message", "Lỗi server: HTTP " + code));
            if (json.has("details")) {
                for (int i = 0; i < json.getJSONArray("details").length(); i++) {
                    sb.append("\n• ").append(json.getJSONArray("details").getString(i));
                }
            }
            return sb.toString();
        } catch (Exception notJson) {
            return code == 403 ? "Bạn không có quyền thực hiện thao tác này" : "Lỗi server: HTTP " + code;
        }
    }

    private static final class Result {
        final int code;
        final String body;
        final String message;

        Result(int code, String body, String message) {
            this.code = code;
            this.body = body;
            this.message = message;
        }
    }
}
