package com.example.andemo.api;

import com.example.andemo.model.AlertAckRequest;
import com.example.andemo.model.PendingAlertDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * API lệnh tìm PDA. Contract: docs/PDA_POLLING_DESIGN.md
 */
public interface AlertApi {

    /** Các lệnh chưa được PDA xác nhận và chưa hết hạn (server lọc theo giờ của server). */
    @GET("api/pda/alerts/pending")
    Call<List<PendingAlertDto>> getPending(@Query("deviceId") String deviceId);

    @POST("api/pda/alerts/{requestId}/ack")
    Call<Void> ack(@Path("requestId") String requestId, @Body AlertAckRequest body);
}
