package com.example.andemo.api;

import com.example.andemo.model.ConfirmDisposalRequest;
import com.example.andemo.model.DisposalDetailDto;
import com.example.andemo.model.DisposalSummaryDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Task 17 / 18: phiếu hủy hàng. Xem docs/DISPOSAL_TASK_17_18.md. */
public interface DisposalApi {

    /** status: REQUESTED / CONFIRMED / CANCELLED, null = tất cả */
    @GET("api/disposals")
    Call<List<DisposalSummaryDto>> list(@Query("status") String status);

    @GET("api/disposals/{disposalNo}")
    Call<DisposalDetailDto> detail(@Path("disposalNo") String disposalNo);

    @POST("api/disposals/{disposalNo}/confirm")
    Call<DisposalDetailDto> confirm(@Path("disposalNo") String disposalNo, @Body ConfirmDisposalRequest body);
}
