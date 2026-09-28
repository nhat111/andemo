package com.example.andemo.websocket;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.andemo.BuildConfig;
import com.example.andemo.alert.AlertAckReporter;
import com.example.andemo.alert.AlertDispatcher;
import com.example.andemo.api.TokenRefresher;
import com.example.andemo.command.CommandNotification;
import com.example.andemo.model.PendingAlertDto;
import com.example.andemo.polling.AlertPoller;
import com.example.andemo.util.DeviceIdProvider;
import com.example.andemo.util.PreferenceManager;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Foreground service giữ kết nối WebSocket tới server để nhận lệnh tìm PDA ngay lập tức.
 *
 * WebSocket chỉ là "chuông cửa"; API pending vẫn là nguồn dữ liệu gốc:
 * - Mỗi lần kết nối được (lần đầu hoặc kết nối lại): poll pending 1 lần để bắt kịp các lệnh
 *   gửi trong lúc mất kết nối. Server không phải tự lưu và gửi lại.
 * - Alarm kiểm tra định kỳ (kể cả khi Doze): poll pending 1 lần và kết nối lại nếu cần.
 *   Khi CPU ngủ, ping của OkHttp không chạy nên kết nối có thể đã chết mà app không biết;
 *   poll định kỳ đảm bảo độ trễ tệ nhất bằng chu kỳ alarm (hệ thống có thể giãn thêm khi Doze).
 *
 * Mọi thay đổi trạng thái kết nối chạy trên 1 thread (wsThread) nên không cần lock.
 * Xem đánh giá đầy đủ trong docs/PDA_WEBSOCKET_DESIGN.md.
 */
public class PdaWebSocketService extends Service {

    private static final String TAG = "PdaWebSocketService";

    // Ngắn hơn timeout của NAT / load balancer (nginx mặc định 60 giây)
    private static final long PING_INTERVAL_MS = 30_000L;
    // Chu kỳ alarm kiểm tra kết nối + poll bắt kịp
    private static final long HEALTH_CHECK_INTERVAL_MS = 3 * 60_000L;
    private static final long MIN_RECONNECT_DELAY_MS = 1_000L;
    private static final long MAX_RECONNECT_DELAY_MS = 60_000L;
    private static final long WAKE_LOCK_TIMEOUT_MS = 20_000L;

    private static final String ACTION_HEALTH_CHECK = "com.example.andemo.websocket.HEALTH_CHECK";

    private final Gson gson = new Gson();
    private final Random random = new Random();

    private OkHttpClient client;
    private HandlerThread wsThread;
    private Handler wsHandler;
    private PowerManager.WakeLock wakeLock;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Chỉ đọc/ghi trên wsThread
    @Nullable
    private WebSocket webSocket;      // khác null = đang kết nối hoặc đã kết nối
    private boolean connected;
    private int reconnectAttempt;
    // Token dùng cho lần kết nối hiện tại: bị 401 thì biết cần làm mới token nào
    @Nullable
    private String connectionToken;

    private final Runnable connectRunnable = this::connectIfNeeded;

    public static void start(Context context) {
        try {
            ContextCompat.startForegroundService(context, new Intent(context, PdaWebSocketService.class));
        } catch (IllegalStateException e) {
            // Android 12+: gọi từ background mà không thuộc trường hợp được phép
            Log.w(TAG, "Cannot start WebSocket service", e);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, PdaWebSocketService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                // WebSocket nằm im lâu là bình thường: không đặt read timeout, dùng ping để phát hiện kết nối chết
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .build();

        wsThread = new HandlerThread("pda-websocket");
        wsThread.start();
        wsHandler = new Handler(wsThread.getLooper());

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "andemo:pda-websocket");
        wakeLock.setReferenceCounted(false);

        registerNetworkCallback();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(CommandNotification.NOTIFICATION_ID, CommandNotification.build(this));
        } catch (IllegalStateException e) {
            // Android 12+: hệ thống khởi động lại service (START_STICKY) hoặc alarm gọi tới
            // trong lúc app không được phép chạy foreground service → dừng, chờ user mở app
            Log.w(TAG, "Not allowed to run in foreground, stopping", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        // Mở app / khởi động máy / alarm: kiểm tra kết nối và bắt kịp lệnh
        wsHandler.post(this::healthCheck);
        scheduleHealthCheckAlarm();
        return START_STICKY;
    }

    /** Chạy trên wsThread. */
    private void healthCheck() {
        if (!isLoggedIn()) {
            return;
        }
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        try {
            // Kết nối có thể đã chết khi CPU ngủ mà chưa bị phát hiện: poll để không lỡ lệnh
            AlertPoller.pollOnce(getApplicationContext());
        } finally {
            releaseWakeLock();
        }
        connectIfNeeded();
    }

    /** Chạy trên wsThread. */
    private void connectIfNeeded() {
        if (!isLoggedIn()) {
            return;
        }
        if (webSocket != null) {
            return; // đang kết nối hoặc đã kết nối
        }
        wsHandler.removeCallbacks(connectRunnable);

        connectionToken = new PreferenceManager(this).getToken();
        Request request = new Request.Builder()
                .url(buildWebSocketUrl())
                .header("Authorization", "Bearer " + connectionToken)
                .build();
        Log.d(TAG, "Connecting to " + request.url());
        webSocket = client.newWebSocket(request, new Listener());
    }

    private HttpUrl buildWebSocketUrl() {
        // OkHttp nhận http/https cho WebSocket và tự nâng cấp thành ws/wss
        return HttpUrl.get(BuildConfig.API_BASE_URL).newBuilder()
                .addPathSegments("ws/pda")
                .addQueryParameter("deviceId", DeviceIdProvider.get(this))
                .build();
    }

    /** Chạy trên wsThread. */
    private void scheduleReconnect(@Nullable Response response) {
        if (!isLoggedIn()) {
            return;
        }
        int code = response == null ? 0 : response.code();
        if (code == 401) {
            // Access token hết hạn: làm mới rồi kết nối lại ngay. Client WebSocket không có
            // TokenAuthenticator như client REST nên phải tự làm ở đây.
            Log.d(TAG, "Handshake rejected: HTTP 401, refreshing token");
            if (TokenRefresher.refresh(getApplicationContext(), connectionToken) != null) {
                reconnectAttempt = 0;
                connectIfNeeded();
                return;
            }
            if (!isLoggedIn()) {
                return; // refresh token cũng hết hạn: đã logout, service tự dừng
            }
            // Lỗi mạng / server khi làm mới: chờ rồi thử lại như bình thường
        }

        long delay;
        if (code == 401 || code == 403) {
            // Không làm mới được token, hoặc không có quyền: không kết nối lại dồn dập
            Log.w(TAG, "Handshake rejected: HTTP " + code);
            delay = MAX_RECONNECT_DELAY_MS;
        } else {
            // Backoff tăng dần 1s, 2s, 4s… tối đa 60s, cộng ngẫu nhiên để hàng loạt PDA
            // không cùng kết nối lại một lúc khi server restart
            long base = Math.min(MAX_RECONNECT_DELAY_MS, MIN_RECONNECT_DELAY_MS << Math.min(reconnectAttempt, 6));
            delay = base + random.nextInt((int) (base / 2) + 1);
        }
        reconnectAttempt++;
        Log.d(TAG, "Reconnect in " + delay + " ms (attempt " + reconnectAttempt + ")");
        wsHandler.removeCallbacks(connectRunnable);
        wsHandler.postDelayed(connectRunnable, delay);
    }

    /** Chạy trên wsThread. */
    private void handleCommand(String text) {
        PendingAlertDto command;
        try {
            command = gson.fromJson(text, PendingAlertDto.class);
        } catch (JsonSyntaxException e) {
            Log.w(TAG, "Invalid message: " + text);
            return;
        }
        if (command == null || !"PDA_FINDER_ALERT".equals(command.getType())) {
            Log.d(TAG, "Ignored message: " + text);
            return;
        }
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        try {
            AlertDispatcher.dispatch(getApplicationContext(), command.getRequestId(),
                    command.getMessage(), command.getStoreCode());
            AlertAckReporter.report(getApplicationContext(), command.getRequestId(),
                    AlertAckReporter.STATUS_DELIVERED);
        } finally {
            releaseWakeLock();
        }
    }

    /**
     * Callback của OkHttp chạy trên thread của OkHttp: chuyển hết về wsThread.
     * Mỗi kết nối có 1 Listener riêng; callback của kết nối cũ (đã bị thay) thì bỏ qua.
     */
    private class Listener extends WebSocketListener {

        @Override
        public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
            wsHandler.post(() -> {
                if (ws != webSocket) {
                    return;
                }
                Log.d(TAG, "Connected");
                connected = true;
                reconnectAttempt = 0;
                // Bắt kịp các lệnh gửi trong lúc chưa kết nối
                AlertPoller.pollOnce(getApplicationContext());
            });
        }

        @Override
        public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
            wsHandler.post(() -> {
                if (ws == webSocket) {
                    handleCommand(text);
                }
            });
        }

        @Override
        public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
            ws.close(1000, null);
        }

        @Override
        public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
            wsHandler.post(() -> onDisconnected(ws, null, "closed " + code + " " + reason));
        }

        @Override
        public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, @Nullable Response response) {
            wsHandler.post(() -> onDisconnected(ws, response, t.getMessage()));
        }
    }

    /** Chạy trên wsThread. */
    private void onDisconnected(WebSocket ws, @Nullable Response response, String reason) {
        if (ws != webSocket) {
            return;
        }
        Log.d(TAG, "Disconnected: " + reason + (connected ? "" : " (was connecting)"));
        webSocket = null;
        connected = false;
        scheduleReconnect(response);
    }

    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                // Có mạng lại: kết nối ngay, bỏ qua thời gian chờ backoff
                wsHandler.post(() -> {
                    reconnectAttempt = 0;
                    connectIfNeeded();
                });
            }
        };
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    private void scheduleHealthCheckAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        long triggerAt = SystemClock.elapsedRealtime() + HEALTH_CHECK_INTERVAL_MS;
        // Không dùng alarm chính xác: cần quyền SCHEDULE_EXACT_ALARM (xem PdaPollingService)
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt,
                buildHealthCheckPendingIntent());
    }

    private PendingIntent buildHealthCheckPendingIntent() {
        Intent intent = new Intent(this, PdaWebSocketService.class);
        intent.setAction(ACTION_HEALTH_CHECK);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean isLoggedIn() {
        if (new PreferenceManager(this).isLoggedIn()) {
            return true;
        }
        Log.d(TAG, "Not logged in, stopping");
        stopSelf();
        return false;
    }

    private void releaseWakeLock() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onDestroy() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(buildHealthCheckPendingIntent());
        }

        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }

        wsHandler.removeCallbacksAndMessages(null);
        wsHandler.post(() -> {
            if (webSocket != null) {
                webSocket.close(1000, "Service stopped");
                webSocket = null;
            }
        });
        wsThread.quitSafely();
        client.dispatcher().executorService().shutdown();
        releaseWakeLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
