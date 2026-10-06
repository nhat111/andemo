package com.example.andemo.polling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.andemo.command.CommandChannel;
import com.example.andemo.log.DeviceLog;
import com.example.andemo.util.PreferenceManager;

/**
 * Khởi động lại kênh nhận lệnh (polling / WebSocket) sau khi máy khởi động lại hoặc app được cập nhật.
 *
 * BOOT_COMPLETED là một trong số ít trường hợp Android 12+ cho phép start foreground service
 * từ background.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        if (new PreferenceManager(context).isLoggedIn()) {
            DeviceLog.d(TAG, "Starting command channel after " + action);
            CommandChannel.start(context);
        }
    }
}
