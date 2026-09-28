package com.example.andemo.alert;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * Điểm vào chung cho mọi kênh nhận lệnh tìm PDA (FCM, polling, …).
 *
 * Kênh nhận lệnh chỉ cần gọi {@link #dispatch}; việc chống trùng và khởi động
 * {@link PdaAlertService} nằm ở đây, nên có thể bật nhiều kênh cùng lúc.
 */
public final class AlertDispatcher {

    private static final String TAG = "AlertDispatcher";

    private AlertDispatcher() {
    }

    /**
     * Kích hoạt alert cho một lệnh tìm PDA.
     *
     * @return true nếu là lệnh mới và đã kích hoạt alert; false nếu requestId đã được xử lý
     */
    public static boolean dispatch(Context context, String requestId, String message, String storeCode) {
        if (requestId == null || requestId.isEmpty()) {
            Log.w(TAG, "Missing requestId, ignore alert");
            return false;
        }
        if (!ProcessedAlertStore.markIfNew(context, requestId)) {
            Log.d(TAG, "Alert already handled, requestId=" + requestId);
            return false;
        }

        Intent intent = new Intent(context, PdaAlertService.class);
        intent.putExtra(PdaAlertService.EXTRA_REQUEST_ID, requestId);
        intent.putExtra(PdaAlertService.EXTRA_MESSAGE, message);
        if (storeCode != null) {
            intent.putExtra(PdaAlertService.EXTRA_STORE_CODE, storeCode);
        }

        try {
            ContextCompat.startForegroundService(context, intent);
            Log.d(TAG, "Started PdaAlertService for requestId=" + requestId);
        } catch (IllegalStateException e) {
            // Android 12+ ném ForegroundServiceStartNotAllowedException (lớp con của
            // IllegalStateException) khi app ở background và không thuộc trường hợp ngoại lệ,
            // ví dụ FCM không phải high priority, hoặc polling khi app chưa được tắt tối ưu pin.
            // Không bắt thì app crash và mất luôn alert → chuyển sang chỉ hiện notification.
            Log.w(TAG, "Cannot start PdaAlertService, showing fallback notification", e);
            PdaAlertService.showFallbackNotification(context, requestId, message);
        }
        return true;
    }
}
