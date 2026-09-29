package com.example.andemo.api;

import com.example.andemo.model.DisposalActionRequest;
import com.example.andemo.model.DisposalDetailDto;
import com.example.andemo.model.DisposalReasonDto;
import com.example.andemo.model.DisposalSaveRequest;
import com.example.andemo.model.DisposalSummaryDto;
import com.example.andemo.model.InventoryItemDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** 폐기 (hủy hàng): 등록 → 수정 / 취소 → 확정 → 확정취소. Xem docs/DISPOSAL_TASK_17_18.md. */
public interface DisposalApi {

    /** status: REGISTERED / CONFIRMED / CANCELLED (hoặc 10 / 20 / 90), null = tất cả */
    @GET("api/disposals")
    Call<List<DisposalSummaryDto>> list(@Query("status") String status);

    @GET("api/disposals/{disposalNo}")
    Call<DisposalDetailDto> detail(@Path("disposalNo") String disposalNo);

    @POST("api/disposals")
    Call<DisposalDetailDto> register(@Body DisposalSaveRequest body);

    @PUT("api/disposals/{disposalNo}")
    Call<DisposalDetailDto> update(@Path("disposalNo") String disposalNo, @Body DisposalSaveRequest body);

    @POST("api/disposals/{disposalNo}/cancel")
    Call<DisposalDetailDto> cancel(@Path("disposalNo") String disposalNo, @Body DisposalActionRequest body);

    @POST("api/disposals/{disposalNo}/confirm")
    Call<DisposalDetailDto> confirm(@Path("disposalNo") String disposalNo, @Body DisposalActionRequest body);

    @POST("api/disposals/{disposalNo}/cancel-confirm")
    Call<DisposalDetailDto> cancelConfirm(@Path("disposalNo") String disposalNo, @Body DisposalActionRequest body);

    @GET("api/disposals/reasons")
    Call<List<DisposalReasonDto>> reasons();

    @GET("api/inventory/{itemCode}")
    Call<InventoryItemDto> item(@Path("itemCode") String itemCode);
}
