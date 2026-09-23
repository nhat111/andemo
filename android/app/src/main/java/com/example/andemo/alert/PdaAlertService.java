package com.example.andemo.alert;

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

import com.example.andemo.R;

/**
 * Foreground Service phát alert khi nhận lệnh tìm PDA.
 *
 * - Hiện notification mức cao nhất
 * - Tăng volume STREAM_ALARM lên max
 * - Phát âm thanh loop
 * - Hiện full-screen AlertActivity
 * - Tự dừng sau timeout hoặc khi user bấm Stop
 */
public class PdaAlertService extends Service {

    private static final String TAG = "PdaAlertService";

    public static final String EXTRA_REQUEST_ID = "requestId";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_STORE_CODE = "storeCode";
    public static final String ACTION_STOP_ALERT = "STOP_ALERT";

    private static final String CHANNEL_ID = "pda_finder_alert_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long TIMEOUT_MS = 60_000L; // 60 giây

    private MediaPlayer mediaPlayer;
    private Handler timeoutHandler;
    private String currentRequestId;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Xử lý action Stop
        if (ACTION_STOP_ALERT.equals(intent.getAction())) {
            Log.d(TAG, "Stop action received");
            stopAlert();
            return START_NOT_STICKY;
        }

        String requestId = intent.getStringExtra(EXTRA_REQUEST_ID);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        String storeCode = intent.getStringExtra(EXTRA_STORE_CODE);

        if (requestId == null || requestId.isEmpty()) {
            Log.w(TAG, "Missing requestId");
            stopSelf();
            return START_NOT_STICKY;
        }

        currentRequestId = requestId;
        if (message == null || message.isEmpty()) {
            message = "PDA đang được tìm kiếm";
        }

        createNotificationChannel();

        Notification notification = buildHighPriorityNotification(message, requestId);
        startForeground(NOTIFICATION_ID, notification);

        forceMaxVolumeAndPlaySound();
        showFullScreenAlert(message, requestId);

        // Timeout tự tắt
        timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutHandler.postDelayed(this::stopAlert, TIMEOUT_MS);

        Log.d(TAG, "Alert started for requestId=" + requestId + ", storeCode=" + storeCode);
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "PDA Finder Alert",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Thông báo tìm kiếm PDA");
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildHighPriorityNotification(String message, String requestId) {
        Intent stopIntent = new Intent(this, PdaAlertService.class);
        stopIntent.setAction(ACTION_STOP_ALERT);
        stopIntent.putExtra(EXTRA_REQUEST_ID, requestId);

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent fullScreenIntent = new Intent(this, AlertActivity.class);
        fullScreenIntent.putExtra(EXTRA_MESSAGE, message);
        fullScreenIntent.putExtra(EXTRA_REQUEST_ID, requestId);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this,
                1,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PDA đang được tìm kiếm")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // thay bằng icon riêng nếu có
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng Alert", stopPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .build();
    }

    private void forceMaxVolumeAndPlaySound() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);
        }

        try {
            // Dùng default alarm sound của hệ thống
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
            );
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to play alert sound", e);
        }
    }

    private void showFullScreenAlert(String message, String requestId) {
        Intent intent = new Intent(this, AlertActivity.class);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void stopAlert() {
        Log.d(TAG, "Stopping alert, requestId=" + currentRequestId);

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

        if (timeoutHandler != null) {
            timeoutHandler.removeCallbacksAndMessages(null);
            timeoutHandler = null;
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopAlert();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
