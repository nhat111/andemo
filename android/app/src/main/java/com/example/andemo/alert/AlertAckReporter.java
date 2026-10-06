package com.example.andemo.alert;

import android.content.Context;

import com.example.andemo.api.AlertApi;
import com.example.andemo.api.ApiClient;
import com.example.andemo.log.DeviceLog;
import com.example.andemo.model.AlertAckRequest;
import com.example.andemo.util.DeviceIdProvider;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Báo trạng thái lệnh tìm PDA về server, để màn hình quản lý biết PDA đã nhận / đã tắt.
 *
 * Bản POC gửi 1 lần, không retry: nếu ack DELIVERED thất bại, lần poll sau server trả lại lệnh
 * và app ack lại (không phát chuông lại nhờ ProcessedAlertStore). Ack STOPPED_BY_USER / TIMED_OUT
 * thất bại thì mất; production nên đưa vào WorkManager để retry.
 */
public final class AlertAckReporter {

    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_STOPPED_BY_USER = "STOPPED_BY_USER";
    public static final String STATUS_TIMED_OUT = "TIMED_OUT";

    private static final String TAG = "AlertAckReporter";

    private AlertAckReporter() {
    }

    public static void report(Context context, String requestId, String status) {
        if (requestId == null || requestId.isEmpty()) {
            return;
        }
        AlertApi api = ApiClient.getClient(context).create(AlertApi.class);
        AlertAckRequest body = new AlertAckRequest(DeviceIdProvider.get(context), status);
        api.ack(requestId, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    DeviceLog.d(TAG, "Ack " + status + " sent for requestId=" + requestId);
                } else {
                    DeviceLog.w(TAG, "Ack " + status + " rejected: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                DeviceLog.w(TAG, "Ack " + status + " failed for requestId=" + requestId + ": " + t.getMessage());
            }
        });
    }
}
