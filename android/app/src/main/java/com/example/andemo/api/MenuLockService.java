package com.example.andemo.api;

import com.example.andemo.model.LockRequest;
import com.example.andemo.model.MenuAccessResponse;
import com.example.andemo.model.MenuDataDto;
import com.example.andemo.model.MenuLockStatusResponse;
import com.example.andemo.model.MenuStatusDto;
import com.example.andemo.model.PinSetupRequest;
import com.example.andemo.model.UnlockRequest;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface MenuLockService {

    @GET("api/menu-lock/status")
    Call<MenuLockStatusResponse> getStatus();

    @GET("api/menu-lock/access/{menuCode}")
    Call<MenuAccessResponse> checkAccess(@Path("menuCode") String menuCode);

    @POST("api/menu-lock/lock")
    Call<List<MenuStatusDto>> lock(@Body LockRequest request);

    @POST("api/menu-lock/unlock")
    Call<List<MenuStatusDto>> unlock(@Body UnlockRequest request);

    @POST("api/menu-lock/otp/request")
    Call<ResponseBody> requestOtp();

    @POST("api/menu-lock/pin")
    Call<ResponseBody> setupPin(@Body PinSetupRequest request);

    @GET("api/store-menus/{menuCode}/data")
    Call<MenuDataDto> getMenuData(@Path("menuCode") String menuCode);
}
