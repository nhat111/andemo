package com.example.andemo.log;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.andemo.BuildConfig;
import com.example.andemo.util.DeviceIdProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Nút "Xuất log": gộp các file log (cũ → mới) thành 1 file rồi mở màn hình chia sẻ của Android
 * (email, Drive, Bluetooth, KakaoTalk…) để gửi cho team điều tra.
 *
 * Tên file có deviceId và thời điểm xuất, ví dụ andemo-log-abc123-websocket-20261006-101500.txt.
 */
public final class LogExporter {

    private static final String TAG = "LogExporter";
    private static final String EXPORT_DIR = "log-export";
    private static final long FLUSH_TIMEOUT_MS = 2_000;

    private LogExporter() {
    }

    public static void share(Activity activity) {
        DeviceLog.i(TAG, "Exporting device log");
        // Đọc / ghi tối đa ~5 MB: không chạy trên main thread
        new Thread(() -> {
            File export = buildExportFile(activity);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                if (export == null) {
                    Toast.makeText(activity, "Không có log để xuất", Toast.LENGTH_SHORT).show();
                    return;
                }
                startShare(activity, export);
            });
        }, "log-export").start();
    }

    private static File buildExportFile(Activity activity) {
        File logDir = DeviceLog.logDir();
        if (logDir == null) {
            return null;
        }
        DeviceLog.flush(FLUSH_TIMEOUT_MS);

        File exportDir = new File(activity.getCacheDir(), EXPORT_DIR);
        if (!exportDir.isDirectory() && !exportDir.mkdirs()) {
            DeviceLog.w(TAG, "Cannot create export directory");
            return null;
        }
        // Chỉ giữ bản xuất mới nhất
        File[] old = exportDir.listFiles();
        if (old != null) {
            for (File file : old) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }

        String time = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File export = new File(exportDir, "andemo-log-" + DeviceIdProvider.get(activity) + "-" + time + ".txt");
        boolean hasContent = false;
        try (OutputStream out = new FileOutputStream(export)) {
            for (int index = DeviceLog.MAX_FILES - 1; index >= 0; index--) {
                File part = DeviceLog.file(logDir, index);
                if (part.isFile()) {
                    copy(part, out);
                    hasContent = true;
                }
            }
        } catch (IOException e) {
            DeviceLog.w(TAG, "Export failed", e);
            return null;
        }
        return hasContent ? export : null;
    }

    private static void copy(File from, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        try (InputStream in = new FileInputStream(from)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static void startShare(Activity activity, File export) {
        Uri uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".logprovider", export);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, export.getName())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Một số app nhận chỉ đọc được file khi URI nằm cả trong ClipData
        send.setClipData(ClipData.newRawUri(export.getName(), uri));
        try {
            activity.startActivity(Intent.createChooser(send, "Xuất log"));
        } catch (RuntimeException e) {
            DeviceLog.w(TAG, "No app to share log", e);
            Toast.makeText(activity, "Không có app nào để gửi log", Toast.LENGTH_SHORT).show();
        }
    }
}
