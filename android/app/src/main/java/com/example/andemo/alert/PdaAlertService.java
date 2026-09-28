package com.example.andemo.alert;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Foreground Service phát alert khi nhận lệnh tìm PDA.
 *
 * - Hiện notification mức cao nhất
 * - Tăng volume STREAM_ALARM lên max, khôi phục volume gốc khi dừng
 * - Phát âm thanh loop
 * - Hiện full-screen AlertActivity
 * - Tự dừng sau timeout hoặc khi user bấm Stop
 *
 * Tại mỗi thời điểm chỉ có 1 alert: alert mới thay thế alert cũ,
 * alert trùng requestId (FCM gửi lại) bị bỏ qua.
 */
public class PdaAlertService extends Service {

    private static final String TAG = "PdaAlertService";

    public static final String EXTRA_REQUEST_ID = "requestId";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_STORE_CODE = "storeCode";
    public static final String ACTION_STOP_ALERT = "STOP_ALERT";

    private static final String CHANNEL_ID = "pda_finder_alert_channel";
    // Channel riêng cho fallback: phải có âm thanh báo thức vì không có MediaPlayer phát giúp
    private static final String FALLBACK_CHANNEL_ID = "pda_finder_alert_fallback_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int FALLBACK_NOTIFICATION_ID = 1002;
    private static final long TIMEOUT_MS = 60_000L; // 60 giây
    private static final String DEFAULT_MESSAGE = "PDA đang được tìm kiếm";

    private MediaPlayer mediaPlayer;
    // Tạo 1 lần cho cả vòng đời service, để luôn huỷ được timeout cũ trước khi đặt timeout mới
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = this::onTimeout;
    // null = không có alert nào đang chạy
    private String currentRequestId;
    // Volume STREAM_ALARM trước khi alert bắt đầu; -1 = chưa lưu
    private int originalAlarmVolume = -1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Hệ thống tạo lại service sau khi process bị kill, không còn thông tin alert
            stopAlert();
            return START_NOT_STICKY;
        }

        // Xử lý action Stop
        if (ACTION_STOP_ALERT.equals(intent.getAction())) {
            Log.d(TAG, "Stop action received");
            // Lấy requestId từ intent: Stop từ notification fallback đến khi service chưa chạy,
            // lúc đó currentRequestId là null
            String stoppedRequestId = intent.getStringExtra(EXTRA_REQUEST_ID);
            if (stoppedRequestId == null) {
                stoppedRequestId = currentRequestId;
            }
            AlertAckReporter.report(this, stoppedRequestId, AlertAckReporter.STATUS_STOPPED_BY_USER);
            stopAlert();
            return START_NOT_STICKY;
        }

        String requestId = intent.getStringExtra(EXTRA_REQUEST_ID);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        String storeCode = intent.getStringExtra(EXTRA_STORE_CODE);
        if (message == null || message.isEmpty()) {
            message = DEFAULT_MESSAGE;
        }

        // Service start bằng startForegroundService() bắt buộc phải gọi startForeground()
        // trong vài giây, kể cả khi sắp dừng ngay. Nếu không, app bị crash
        // ("did not then call Service.startForeground()"). Vì vậy gọi trước khi validate.
        createNotificationChannels(this);
        startForeground(NOTIFICATION_ID, buildHighPriorityNotification(message, requestId));

        if (requestId == null || requestId.isEmpty()) {
            Log.w(TAG, "Missing requestId");
            stopAlert();
            return START_NOT_STICKY;
        }

        // FCM có thể gửi lại cùng message: không phát lại, không reset timeout
        if (requestId.equals(currentRequestId)) {
            Log.d(TAG, "Duplicate alert ignored, requestId=" + requestId);
            return START_NOT_STICKY;
        }

        // Đang có alert khác thì dừng âm thanh + timeout cũ trước. Nếu không, MediaPlayer cũ
        // mất tham chiếu và kêu mãi (stopAlert chỉ release được player hiện tại).
        releasePlayer();
        timeoutHandler.removeCallbacks(timeoutRunnable);

        currentRequestId = requestId;
        forceMaxVolumeAndPlaySound();
        showFullScreenAlert(message, requestId);
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        Log.d(TAG, "Alert started for requestId=" + requestId + ", storeCode=" + storeCode);
        // Process bị kill thì không cần hệ thống tạo lại service: alert đã mất, không phát lại được
        return START_NOT_STICKY;
    }

    /**
     * Dùng khi không start được foreground service. Ví dụ trên Android 12+, FCM message
     * không phải high priority thì app không được start foreground service từ background.
     *
     * Chỉ post notification: FLAG_INSISTENT làm âm thanh của channel lặp lại tới khi
     * notification bị gỡ, setTimeoutAfter tự gỡ sau TIMEOUT_MS.
     * Hạn chế so với service: không tăng được volume, âm thanh phụ thuộc setting của channel.
     */
    @SuppressLint("MissingPermission") // đã kiểm tra bằng areNotificationsEnabled()
    public static void showFallbackNotification(Context context, String requestId, String message) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) {
            // Android 13+ chưa được cấp POST_NOTIFICATIONS: notification sẽ không hiện
            Log.w(TAG, "Notifications disabled, cannot show fallback alert for requestId=" + requestId);
            return;
        }
        createNotificationChannels(context);

        Notification notification = new NotificationCompat.Builder(context, FALLBACK_CHANNEL_ID)
                .setContentTitle(DEFAULT_MESSAGE)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Âm thanh cho Android 7.x; từ Android 8 âm thanh lấy theo channel
                .setSound(getAlarmSoundUri(), AudioManager.STREAM_ALARM)
                .setTimeoutAfter(TIMEOUT_MS)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng Alert",
                        buildStopPendingIntent(context, requestId))
                .setFullScreenIntent(buildFullScreenPendingIntent(context, message, requestId), true)
                .build();
        notification.flags |= Notification.FLAG_INSISTENT;

        manager.notify(FALLBACK_NOTIFICATION_ID, notification);
        Log.d(TAG, "Fallback alert notification shown for requestId=" + requestId);
    }

    private static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) {
                return;
            }

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "PDA Finder Alert",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Thông báo tìm kiếm PDA");
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);

            NotificationChannel fallbackChannel = new NotificationChannel(
                    FALLBACK_CHANNEL_ID,
                    "PDA Finder Alert (dự phòng)",
                    NotificationManager.IMPORTANCE_HIGH
            );
            fallbackChannel.setDescription("Dùng khi không khởi động được alert service");
            fallbackChannel.enableVibration(true);
            fallbackChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            fallbackChannel.setSound(getAlarmSoundUri(), alarmAudioAttributes());
            manager.createNotificationChannel(fallbackChannel);
        }
    }

    private Notification buildHighPriorityNotification(String message, String requestId) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(DEFAULT_MESSAGE)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // thay bằng icon riêng nếu có
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng Alert",
                        buildStopPendingIntent(this, requestId))
                .setFullScreenIntent(buildFullScreenPendingIntent(this, message, requestId), true)
                .build();
    }

    private static PendingIntent buildStopPendingIntent(Context context, String requestId) {
        Intent stopIntent = new Intent(context, PdaAlertService.class);
        stopIntent.setAction(ACTION_STOP_ALERT);
        stopIntent.putExtra(EXTRA_REQUEST_ID, requestId);

        return PendingIntent.getService(
                context,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent buildFullScreenPendingIntent(Context context, String message,
                                                              String requestId) {
        Intent fullScreenIntent = new Intent(context, AlertActivity.class);
        fullScreenIntent.putExtra(EXTRA_MESSAGE, message);
        fullScreenIntent.putExtra(EXTRA_REQUEST_ID, requestId);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        return PendingIntent.getActivity(
                context,
                1,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static Uri getAlarmSoundUri() {
        // Dùng default alarm sound của hệ thống
        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmUri == null) {
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        return alarmUri;
    }

    private static AudioAttributes alarmAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    private void forceMaxVolumeAndPlaySound() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            // Chỉ lưu ở alert đầu tiên. Alert mới đến khi alert cũ đang kêu thì volume
            // lúc đó đã là max, lưu lại sẽ làm mất giá trị gốc thật.
            if (originalAlarmVolume < 0) {
                originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            }
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);
            } catch (SecurityException e) {
                // Một số trạng thái Do Not Disturb không cho app đổi volume: vẫn phát ở volume hiện tại
                Log.w(TAG, "Not allowed to change alarm volume", e);
            }
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, getAlarmSoundUri());
            mediaPlayer.setAudioAttributes(alarmAudioAttributes());
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to play alert sound", e);
            releasePlayer();
        }
    }

    private void restoreAlarmVolume() {
        if (originalAlarmVolume < 0) {
            return;
        }
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0);
            } catch (SecurityException e) {
                Log.w(TAG, "Not allowed to restore alarm volume", e);
            }
        }
        originalAlarmVolume = -1;
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing MediaPlayer", e);
            }
            mediaPlayer = null;
        }
    }

    private void showFullScreenAlert(String message, String requestId) {
        Intent intent = new Intent(this, AlertActivity.class);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /** Dọn toàn bộ tài nguyên của alert. Gọi nhiều lần vẫn an toàn. */
    private void cleanUp() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
        releasePlayer();
        restoreAlarmVolume();
        currentRequestId = null;
        // Gỡ luôn notification fallback (nếu có) khi user bấm Stop
        NotificationManagerCompat.from(this).cancel(FALLBACK_NOTIFICATION_ID);
    }

    private void onTimeout() {
        AlertAckReporter.report(this, currentRequestId, AlertAckReporter.STATUS_TIMED_OUT);
        stopAlert();
    }

    private void stopAlert() {
        Log.d(TAG, "Stopping alert, requestId=" + currentRequestId);
        cleanUp();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        // Hệ thống có thể huỷ service mà không qua stopAlert: vẫn phải tắt âm thanh + trả volume
        cleanUp();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
