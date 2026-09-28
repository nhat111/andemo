package com.example.andemo.polling;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.andemo.util.PreferenceManager;

/**
 * Foreground service chạy suốt khi user đã login, định kỳ hỏi server có lệnh tìm PDA không.
 *
 * Lịch poll dùng 2 cơ chế song song, cái nào đến trước thì poll:
 * - Handler.postDelayed: đúng giờ khi máy thức. Nhưng Handler tính theo thời gian CPU chạy,
 *   nên khi CPU ngủ (màn hình tắt, Doze) nó có thể trễ vô thời hạn.
 * - AlarmManager.setAndAllowWhileIdle: đánh thức được CPU kể cả khi Doze, nhưng hệ thống
 *   giới hạn tần suất khi Doze (vài phút một lần, tùy phiên bản Android).
 * Ngoài ra, có mạng trở lại thì poll ngay.
 *
 * Xem đánh giá đầy đủ trong docs/PDA_POLLING_DESIGN.md.
 */
public class PdaPollingService extends Service {

    private static final String TAG = "PdaPollingService";

    // 30 giây: cân bằng giữa độ trễ, pin và tải server. Khi Doze, khoảng cách thực tế dài hơn nhiều.
    private static final long POLL_INTERVAL_MS = 30_000L;
    // Giữ CPU thức tối đa chừng này cho mỗi lần poll (đủ cho 1 request HTTP)
    private static final long POLL_WAKE_LOCK_TIMEOUT_MS = 20_000L;

    private static final String ACTION_POLL_NOW = "com.example.andemo.polling.POLL_NOW";
    private static final String CHANNEL_ID = "pda_polling_channel";
    private static final int NOTIFICATION_ID = 2001;

    private HandlerThread pollThread;
    private Handler pollHandler;
    private PowerManager.WakeLock pollWakeLock;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final Runnable pollRunnable = this::pollAndScheduleNext;

    /** Bắt đầu polling. Gọi khi app đang mở (activity hiển thị) hoặc từ BOOT_COMPLETED. */
    public static void start(Context context) {
        try {
            ContextCompat.startForegroundService(context, new Intent(context, PdaPollingService.class));
        } catch (IllegalStateException e) {
            // Android 12+: gọi từ background mà không thuộc trường hợp được phép
            Log.w(TAG, "Cannot start polling service", e);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, PdaPollingService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Poll chạy request HTTP đồng bộ nên cần thread riêng, không chạy trên main thread
        pollThread = new HandlerThread("pda-poller");
        pollThread.start();
        pollHandler = new Handler(pollThread.getLooper());

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        pollWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "andemo:pda-poll");
        pollWakeLock.setReferenceCounted(false);

        registerNetworkCallback();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (IllegalStateException e) {
            // Android 12+: hệ thống khởi động lại service (START_STICKY) hoặc alarm gọi tới
            // trong lúc app không được phép chạy foreground service → dừng, chờ user mở app
            Log.w(TAG, "Not allowed to run in foreground, stopping", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        // Mọi lần start (mở app, alarm, khởi động máy) đều poll ngay
        pollNow();
        // Bị hệ thống kill thì xin khởi động lại để tiếp tục poll
        return START_STICKY;
    }

    private void pollNow() {
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.post(pollRunnable);
    }

    /** Chạy trên pollThread. */
    private void pollAndScheduleNext() {
        if (!new PreferenceManager(this).isLoggedIn()) {
            Log.d(TAG, "Not logged in, stopping polling");
            stopSelf();
            return;
        }

        pollWakeLock.acquire(POLL_WAKE_LOCK_TIMEOUT_MS);
        try {
            AlertPoller.pollOnce(getApplicationContext());
        } finally {
            if (pollWakeLock.isHeld()) {
                pollWakeLock.release();
            }
        }

        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        scheduleWakeUpAlarm();
    }

    /** Đánh thức máy để poll kể cả khi Doze. Mỗi lần gọi thay thế alarm cũ (cùng PendingIntent). */
    private void scheduleWakeUpAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        long triggerAt = SystemClock.elapsedRealtime() + POLL_INTERVAL_MS;
        // Không dùng alarm chính xác (setExact…): từ Android 12 cần quyền SCHEDULE_EXACT_ALARM,
        // và Android 14 mặc định không cấp quyền này cho app không phải báo thức/lịch
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt,
                buildPollNowPendingIntent());
    }

    private PendingIntent buildPollNowPendingIntent() {
        Intent intent = new Intent(this, PdaPollingService.class);
        intent.setAction(ACTION_POLL_NOW);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
                // Có mạng lại: poll ngay để nhận các lệnh gửi trong lúc mất mạng
                Log.d(TAG, "Network available, polling now");
                pollNow();
            }
        };
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_LOW: không kêu, không hiện heads-up; chỉ là notification thường trực
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Chờ lệnh tìm PDA",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Hiển thị khi app đang chờ lệnh tìm PDA từ server");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Andemo")
                .setContentText("Đang chờ lệnh tìm PDA")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    @Override
    public void onDestroy() {
        pollHandler.removeCallbacksAndMessages(null);
        pollThread.quitSafely();

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(buildPollNowPendingIntent());
        }

        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }

        if (pollWakeLock.isHeld()) {
            pollWakeLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
