package com.example.andemo.api;

import com.example.andemo.model.NxRequest;
import com.example.andemo.model.NxResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Gọi service Nexacro cũ (*.do) qua cổng chuyển đổi JSON của backend.
 * Tương đương transaction(svcID, "svc::" + service + ".do", ...):
 *
 * <pre>
 * api.call("product/search", new NxRequest()...)   ⇒  backend gọi product/search.do
 * </pre>
 */
public interface NexacroGatewayApi {

    // encoded = true: giữ nguyên dấu "/" trong tên service (product/search)
    @POST("api/nx/{service}")
    Call<NxResponse> call(@Path(value = "service", encoded = true) String service, @Body NxRequest body);
}
