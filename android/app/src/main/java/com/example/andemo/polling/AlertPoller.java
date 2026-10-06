package com.example.andemo.polling;

import android.content.Context;

import com.example.andemo.alert.AlertAckReporter;
import com.example.andemo.alert.AlertDispatcher;
import com.example.andemo.api.AlertApi;
import com.example.andemo.api.ApiClient;
import com.example.andemo.log.DeviceLog;
import com.example.andemo.model.PendingAlertDto;
import com.example.andemo.util.DeviceIdProvider;

import java.io.IOException;
import java.util.List;

import retrofit2.Response;

/**
 * Hỏi server 1 lần xem có lệnh tìm PDA nào đang chờ không.
 *
 * Gọi đồng bộ (execute) nên KHÔNG được gọi trên main thread.
 */
public final class AlertPoller {

    private static final String TAG = "AlertPoller";

    private AlertPoller() {
    }

    public static void pollOnce(Context context) {
        AlertApi api = ApiClient.getClient(context).create(AlertApi.class);
        Response<List<PendingAlertDto>> response;
        try {
            response = api.getPending(DeviceIdProvider.get(context), DeviceIdProvider.getDeviceName()).execute();
        } catch (IOException e) {
            // Mất mạng, timeout, server ngủ… lần poll sau thử lại
            DeviceLog.w(TAG, "Poll failed: " + e.getMessage());
            return;
        }

        if (!response.isSuccessful() || response.body() == null) {
            DeviceLog.w(TAG, "Poll rejected: HTTP " + response.code());
            return;
        }

        List<PendingAlertDto> alerts = response.body();
        DeviceLog.d(TAG, "Poll ok, pending alerts: " + alerts.size());
        for (PendingAlertDto alert : alerts) {
            String requestId = alert.getRequestId();
            if (requestId == null || requestId.isEmpty()) {
                continue;
            }
            AlertDispatcher.dispatch(context, requestId, alert.getMessage(), alert.getStoreCode());
            // Ack cả khi lệnh đã xử lý trước đó: server vẫn trả lại nghĩa là lần ack trước thất bại
            AlertAckReporter.report(context, requestId, AlertAckReporter.STATUS_DELIVERED);
        }
    }
}
