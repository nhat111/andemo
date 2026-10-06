package com.example.andemo.log;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.andemo.BuildConfig;
import com.example.andemo.util.DeviceIdProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Log trên thiết bị: ghi ra Logcat như {@link Log}, đồng thời lưu vào file trong bộ nhớ app.
 *
 * Logcat là bộ nhớ vòng, vài phút tới vài giờ là bị ghi đè, và PDA ngoài hiện trường không cắm
 * USB được. File log giữ lại để điều tra sự cố ("máy X không kêu lúc 10 giờ"): lấy ra bằng nút
 * "Xuất log" ({@link LogExporter}).
 *
 * - Vị trí: filesDir/logs/device.log, xoay vòng device.1.log … device.4.log, mỗi file tối đa 1 MB
 *   (tổng tối đa ~5 MB, file cũ nhất bị xóa).
 * - Ghi file trên 1 thread riêng nên gọi được từ main thread.
 * - Mỗi lần app khởi động ghi 1 dòng header: version, flavor, deviceId, model, Android version.
 * - Crash (uncaught exception) được ghi kèm stack trace trước khi app chết.
 * - Không ghi token, mật khẩu: dòng nào cần lộ dữ liệu nhạy cảm thì dùng {@link Log} trực tiếp.
 *
 * Chưa gửi log lên server: khách sẽ bổ sung requirement riêng nếu cần.
 */
public final class DeviceLog {

    private static final String TAG = "DeviceLog";

    static final String DIR_NAME = "logs";
    static final String FILE_PREFIX = "device";
    static final String FILE_SUFFIX = ".log";
    static final int MAX_FILES = 5;
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final long CRASH_FLUSH_TIMEOUT_MS = 500;

    private static final Object LOCK = new Object();
    // null = chưa init: chỉ ghi Logcat
    @Nullable
    private static volatile File logDir;
    @Nullable
    private static volatile ExecutorService writer;

    private DeviceLog() {
    }

    /** Gọi 1 lần trong Application.onCreate. */
    public static void init(Context context) {
        if (writer != null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        File dir = new File(appContext.getFilesDir(), DIR_NAME);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "Cannot create log directory, file logging disabled");
            return;
        }
        logDir = dir;
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "device-log");
            thread.setDaemon(true);
            return thread;
        });
        installCrashHandler();

        i(TAG, "=== App start: " + BuildConfig.APPLICATION_ID
                + " " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + ", channel=" + BuildConfig.PDA_COMMAND_CHANNEL
                + ", debug=" + BuildConfig.DEBUG
                + ", deviceId=" + DeviceIdProvider.get(appContext)
                + ", device=" + Build.MANUFACTURER + " " + Build.MODEL
                + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
    }

    public static void d(String tag, String message) {
        Log.d(tag, message);
        enqueue('D', tag, message, null);
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
        enqueue('I', tag, message, null);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
        enqueue('W', tag, message, null);
    }

    public static void w(String tag, String message, Throwable error) {
        Log.w(tag, message, error);
        enqueue('W', tag, message, error);
    }

    public static void e(String tag, String message, Throwable error) {
        Log.e(tag, message, error);
        enqueue('E', tag, message, error);
    }

    /** Thư mục chứa file log; null nếu chưa init hoặc không tạo được. */
    @Nullable
    static File logDir() {
        return logDir;
    }

    /** Chờ các dòng log đang xếp hàng được ghi xong (dùng trước khi xuất log). */
    static void flush(long timeoutMs) {
        ExecutorService current = writer;
        if (current == null) {
            return;
        }
        try {
            current.submit(() -> { }).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Log.w(TAG, "Flush timed out: " + e.getMessage());
        }
    }

    /** File thứ index: 0 = device.log (mới nhất), 1 = device.1.log, … */
    static File file(File dir, int index) {
        return new File(dir, index == 0 ? FILE_PREFIX + FILE_SUFFIX : FILE_PREFIX + "." + index + FILE_SUFFIX);
    }

    private static void enqueue(char level, String tag, String message, @Nullable Throwable error) {
        ExecutorService current = writer;
        if (current == null) {
            return;
        }
        // Lấy thời gian và tên thread lúc gọi, không phải lúc thread ghi file chạy tới
        long time = System.currentTimeMillis();
        String thread = Thread.currentThread().getName();
        try {
            current.execute(() -> write(format(time, level, thread, tag, message, error)));
        } catch (RuntimeException e) {
            // Executor đã dừng (đang crash): bỏ qua, dòng này vẫn có trong Logcat
        }
    }

    private static String format(long time, char level, String thread, String tag, String message,
                                 @Nullable Throwable error) {
        // SimpleDateFormat không an toàn đa luồng: tạo mới mỗi lần, chi phí nhỏ so với ghi file
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(new Date(time));
        StringBuilder line = new StringBuilder()
                .append(timestamp).append(' ').append(level).append('/').append(tag)
                .append(" [").append(thread).append("] ").append(message).append('\n');
        if (error != null) {
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            line.append(stack);
        }
        return line.toString();
    }

    private static void write(String text) {
        File dir = logDir;
        if (dir == null) {
            return;
        }
        synchronized (LOCK) {
            File current = file(dir, 0);
            if (current.length() >= MAX_FILE_BYTES) {
                rotate(dir);
            }
            try (Writer out = new OutputStreamWriter(new FileOutputStream(current, true), StandardCharsets.UTF_8)) {
                out.write(text);
            } catch (IOException e) {
                // Bộ nhớ đầy…: không ném lỗi ra ngoài, log file chỉ là phụ
                Log.w(TAG, "Cannot write log file: " + e.getMessage());
            }
        }
    }

    /** device.3.log → device.4.log, …, device.log → device.1.log; file cũ nhất bị xóa. */
    private static void rotate(File dir) {
        File oldest = file(dir, MAX_FILES - 1);
        if (oldest.exists() && !oldest.delete()) {
            Log.w(TAG, "Cannot delete " + oldest.getName());
        }
        for (int index = MAX_FILES - 2; index >= 0; index--) {
            File from = file(dir, index);
            if (from.exists() && !from.renameTo(file(dir, index + 1))) {
                Log.w(TAG, "Cannot rotate " + from.getName());
            }
        }
    }

    private static void installCrashHandler() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                // Ghi trực tiếp (không qua executor) vì process sắp chết
                ExecutorService current = writer;
                if (current != null) {
                    current.shutdown();
                    current.awaitTermination(CRASH_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }
                write(format(System.currentTimeMillis(), 'E', thread.getName(), TAG, "App crashed", error));
            } catch (Throwable ignored) {
                // Không để lỗi khi ghi log che mất crash gốc
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }
}
