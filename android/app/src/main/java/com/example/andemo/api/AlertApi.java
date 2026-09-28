package com.example.andemo.api;

import com.example.andemo.model.AlertAckRequest;
import com.example.andemo.model.AlertStatusDto;
import com.example.andemo.model.CreateAlertRequest;
import com.example.andemo.model.FcmTokenRequest;
import com.example.andemo.model.PdaDeviceDto;
import com.example.andemo.model.PendingAlertDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * API lệnh tìm PDA. Contract: docs/PDA_POLLING_DESIGN.md
 */
public interface AlertApi {

    // ----- Phía PDA -----

    /**
     * Các lệnh chưa được PDA xác nhận và chưa hết hạn (server lọc theo giờ của server).
     * Server cũng ghi nhận PDA này vừa liên lạc (deviceName, thời điểm) cho màn hình requester.
     */
    @GET("api/pda/alerts/pending")
    Call<List<PendingAlertDto>> getPending(@Query("deviceId") String deviceId,
                                           @Query("deviceName") String deviceName);

    @POST("api/pda/alerts/{requestId}/ack")
    Call<Void> ack(@Path("requestId") String requestId, @Body AlertAckRequest body);

    /** Đăng ký FCM token của PDA (chế độ pdaChannel=fcm). Token null = hủy đăng ký. */
    @PUT("api/pda/devices/{deviceId}/fcm-token")
    Call<Void> updateFcmToken(@Path("deviceId") String deviceId, @Body FcmTokenRequest body);

    // ----- Phía requester (ADMIN) -----

    /** Các PDA server biết, PDA liên lạc gần nhất đứng đầu. */
    @GET("api/pda/devices")
    Call<List<PdaDeviceDto>> getDevices();

    @POST("api/pda/alerts")
    Call<AlertStatusDto> createAlert(@Body CreateAlertRequest body);

    @GET("api/pda/alerts/{requestId}")
    Call<AlertStatusDto> getAlert(@Path("requestId") String requestId);
}
